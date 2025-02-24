SCNvim {
    classvar <>netAddr;
    classvar <currentPath;
    classvar <>port;

    *currentPath_ { arg newPath;
        currentPath = newPath;
        Document.current;
        Mod.switchToMod(newPath);
    }

    *savePath { arg path;
      SaveHooks.savePath(path);
      Mod.reloadOnSave(path)
    }

    *sendJSON {|data|
        var json;
        if (netAddr.isNil) {
            netAddr = NetAddr("127.0.0.1", SCNvim.port);
        };
        json = SCNvimJSON.stringify(data);
        if (json.notNil) {
            netAddr.sendRaw(json);
        } {
            "[scnvim] could not encode to json: %".format(data).warn;
        }
    }

    *luaeval{|luacode|
        SCNvim.sendJSON((action: "luaeval", args: luacode))
    }

    *eval {|expr, callback_id|
        var result = expr.interpret;
        result = (action: "eval", args: (result: result, id: callback_id));
        SCNvim.sendJSON(result);
    }

    *updateStatusLine {arg interval=1;
        var stlFunc = {
            var serverStatus, data;
            var peakCPU, avgCPU, numUGens, numSynths;
            var server = Server.default;
            var cmd;
            if (server.serverRunning) {
                peakCPU = server.peakCPU.trunc(0.01);
                avgCPU = server.avgCPU.trunc(0.01);
                numUGens = "%u".format(server.numUGens);
                numSynths = "%s".format(server.numSynths);
                serverStatus = "%\\% %\\% % %".format(
                    peakCPU, avgCPU, numUGens, numSynths
                );
                cmd = "require'scnvim.statusline'.set_server_status('%')".format(serverStatus);
                SCNvim.luaeval(cmd);
            }
        };
        SkipJack(stlFunc, interval, name: "scnvim_statusline");
    }

    *generateAssets {|cacheDir, snippetFormat = "ultisnips"|
        var tagsPath = cacheDir +/+ "tags";
        var syntaxPath = cacheDir +/+ "classes.vim";
        var snippetPath = cacheDir;
        case
        {snippetFormat == "ultisnips"}
        {
            snippetPath = snippetPath +/+ "supercollider.snippets";
        }
        {snippetFormat == "snippets.nvim" or: { snippetFormat == "luasnip" }}
        {
            snippetPath = snippetPath +/+ "scnvim_snippets.lua";
        }
        {
            "Unrecognized snippet format: '%'".format(snippetFormat).warn;
            snippetPath = nil;
        };
        Routine.run {
            SCNvim.generateTags(tagsPath);
            SCNvim.generateSyntax(syntaxPath);
            if (snippetPath.notNil) {
                SCNvim.generateSnippets(snippetPath, snippetFormat);
            }
        };
    }

    *generateSyntax {arg outputPath;
        var path, file, classes;
        classes = Class.allClasses.collect {|class|
            class.asString ++ " ";
        };
        path = outputPath.standardizePath;
        file = File.open(path, "w");
        file.write("syn keyword scObject ");
        file.putAll(classes);
        file.close;
        "Generated syntax file: %".format(path).postln;
    }

    // copied from SCVim.sc
    // modified to produce a sorted tags file
    // GPLv3 license
    *generateTags {arg outputPath;
        var tagPath, tagFile;
        var tags = [];

        tagPath = outputPath ? "~/.sctags";
        tagPath = tagPath.standardizePath;

        tagFile = File.open(tagPath, "w");

        tagFile.write('!_TAG_FILE_FORMAT	2	/extended format; --format=1 will not append ;" to lines/'.asString ++ Char.nl);
        tagFile.write("!_TAG_FILE_SORTED	1	/0=unsorted, 1=sorted, 2=foldcase/" ++ Char.nl);
        tagFile.write("!_TAG_PROGRAM_AUTHOR Stephen Lumenta /stephen.lumenta@gmail.com/" ++ Char.nl);
        tagFile.write("!_TAG_PROGRAM_NAME   SCNVim.sc//" ++ Char.nl);
        tagFile.write("!_TAG_PROGRAM_URL	https://github.com/davidgranstrom/scnvim" ++ Char.nl);
        tagFile.write("!_TAG_PROGRAM_VERSION	2.0//" ++ Char.nl);

        Class.allClasses.do {arg klass;
            var klassName, klassFilename, klassSearchString;
            var result;

            klassName = klass.asString;
            klassFilename = klass.filenameSymbol;
            // use a symbol and convert to string to avoid the "open ended
            // string" error on class lib compiliation
            klassSearchString = '/^%/;"%%'.asString.format(klassName, Char.tab, "c");

            result = klassName ++ Char.tab ++ klassFilename ++ Char.tab ++ klassSearchString ++ Char.nl;
            tags = tags.add(result);

            klass.methods.do {arg meth;
                var methName, methFilename, methSearchString;
                methName = meth.name;
                methFilename = meth.filenameSymbol;
                methSearchString = '/% {/;"%%'.asString.format(methName, Char.tab, "m");
                result = methName ++ Char.tab ++ methFilename ++ Char.tab ++ methSearchString ++ Char.nl;
                tags = tags.add(result);
            }
        };

        tags = tags.sort;
        tagFile.putAll(tags);
        tagFile.close;
        "Generated tags file: %".format(tagPath).postln;
    }

    *generateSnippets {arg outputPath, snippetFormat;
        var file, path;
        var snippets = [];

        path = outputPath ? "~/.scsnippets";
        path = path.standardizePath;
        file = File.open(path, "w");
        snippetFormat = snippetFormat ? "ultisnips";

        Class.allClasses.do {arg klass;
            var className, argList, signature;
            if (klass.asString.beginsWith("Meta_").not) {
                // collect all creation methods
                klass.class.methods.do {arg meth;
                    var index, snippet;
                    var snippetName;
                    // classvars with getter/setters produces an error
                    // since we're only interested in creation methods we skip them
                    try {
                        snippetName = "%.%".format(klass, meth.name);
                        signature = Help.methodArgs(snippetName);
                    };

                    if (signature.notNil and:{signature.isEmpty.not}) {
                        index = signature.find("(");
                        className = signature[..index - 1];
                        className = className.replace("*", ".").replace(" ", "");

                        if(snippetFormat == "luasnip", {
                          // LuaSnip
                          argList = signature[index..];
                          argList = argList.replace("(", "").replace(")", "");
                          argList = argList.split($,);
                          argList = argList.collect {|a, i|
                            var scArg = a.replace(" ", "").split($:);
                            var scArgName = scArg[0];
                            var scArgVal = scArg[1];
                            var snipArgument;

                            snipArgument = "t(\"%:\"),".format(scArgName);
                            snipArgument = snipArgument ++ "i(%, \"%\")".format(i+1, scArgVal);

                            // Only add text node with comma if not last item
                            if(i+1 != argList.size, {
                              snipArgument = snipArgument ++ ",t(\", \")"
                            });

                            snipArgument
                          };

                          argList = "t(\"(\")," ++ argList.join(", ") ++ ", t(\")\"),";
                          snippet = "t(\"%\"),".format(className) ++ argList;

                          // Not sure why this is necessary but some snippets generate new lines?
                          snippet = snippet.replace(Char.nl, "");

                        }, {
                          // UltiSnips, Snippets.nvim
                          argList = signature[index..];
                          argList = argList.replace("(", "").replace(")", "");
                          argList = argList.split($,);
                          argList = argList.collect {|a, i|
                            "${%:%}".format(i+1, a)
                          };
                          argList = "(" ++ argList.join(", ") ++ ")";
                          snippet = className ++ argList;

                        });

                        case
                        {snippetFormat == "ultisnips"} {
                            snippet = "snippet %\n%\nendsnippet\n".format(snippetName, snippet);
                        }
                        {snippetFormat == "snippets.nvim"} {
                            snippet = "['%'] = [[%]];\n".format(snippetName, snippet);
                        }
                        {snippetFormat == "luasnip"} {
                          var description = "Snippet for %, auto generated by SCNvim".format(snippetName);
                            snippet = "s( {trig = \"%\", name = \"%\", dscr = \"%\" }, {%}),".format(snippetName, snippetName, description, snippet);
                        };
                        snippets = snippets.add(snippet ++ Char.nl);
                    };
                };
            };
        };

        case
        {snippetFormat == "ultisnips"} {
            file.write("# SuperCollider snippets" ++ Char.nl);
            file.write("# Snippet generator: SCNvim.sc" ++ Char.nl);
            file.putAll(snippets);
        }
        {snippetFormat == "luasnip"} {
            file.write("-- SuperCollider snippets for LuaSnip" ++ Char.nl);
            file.write("-- Snippet generator: SCNvim.sc" ++ Char.nl);
            file.write("local ls = require'luasnip'" ++ Char.nl);
            file.write("local s = ls.snippet " ++ Char.nl);
            file.write("local i = ls.insert_node" ++ Char.nl);
            file.write("local t = ls.text_node" ++ Char.nl);
            file.write("local snippets = {" ++ Char.nl);
            file.putAll(snippets);
            file.write("}" ++ Char.nl);
            file.write("return snippets");
        }
        {snippetFormat == "snippets.nvim" } {
            file.write("-- SuperCollider snippets for Snippets.nvim" ++ Char.nl);
            file.write("-- Snippet generator: SCNvim.sc" ++ Char.nl);
            file.write("local snippets = {" ++ Char.nl);
            file.putAll(snippets);
            file.write("}" ++ Char.nl);
            file.write("return snippets");
        };
        file.close;
        "Generated snippets file: %".format(path).postln;
    }
}
