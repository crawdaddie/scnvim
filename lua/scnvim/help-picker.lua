-- local t = require('telescope')
-- local fn = vim.fn

local uv = vim.loop
local api = vim.api
local helpSource = '/Users/adam/Library/Application Support/SuperCollider/Help'
local path = require 'scnvim.path'

local utils = require 'scnvim.utils'


local M = {}

local methods

local function get_docmap(target_dir)
  if methods then
    return methods
  end
  local stat = uv.fs_stat(target_dir)
  assert(stat, 'Could not find docmap.json')
  local fd = uv.fs_open(target_dir, 'r', 0)
  local size = stat.size
  local file = uv.fs_read(fd, size, 0)
  local ok, docmap = pcall(vim.fn.json_decode, file)
  uv.fs_close(fd)
  if not ok then
    error(docmap)
  end
  local results = {}

  for t, value in pairs(docmap) do
    table.insert(results, {
      value.title,
      string.format('%s/%s.txt', helpSource, t),
      value.title,
      value.title
    })
    local methodName = ''
    for _, method in ipairs(value.methods) do
      methodName = method:gsub('[-?]', '')
      table.insert(results, {
        string.format('%s.%s', value.title, methodName),
        string.format('%s/%s.txt', helpSource, t),
        methodName,
        value.title
        -- methodName:gsub('*', value.title)
        -- destpath,
        -- string.format('^\\.%s', method),
      })
    end
  end
  methods = results

  return results
end

function M.open_picker(subject)
  local telescope = require('telescope')
  local pickers = require("telescope.pickers")
  local finders = require("telescope.finders")
  local previewers = require("telescope.previewers")
  local conf = require("telescope.config").values
  local actions = require("telescope.actions")
  local action_state = require("telescope.actions.state")

  local opts = {}

  pickers.new(opts, {
    default_text = subject,
    prompt_title = "methods",
    finder = finders.new_table {
      results = get_docmap(path.concat(helpSource, 'docmap.json')),
      entry_maker = function(entry)
        return {
          value = entry[1],
          display = entry[1],
          ordinal = entry[1],
          class = entry[4],
          method = entry[3],
          path = entry[2],
          -- filename = entry[3],
        }
      end
    },
    sorter = conf.generic_sorter(opts),
    previewer = previewers.new_termopen_previewer({
      get_command = function(entry, status)
        return { 'cat', entry.path }
      end
    }),
    attach_mappings = function(prompt_bufnr, map)
      actions.select_default:replace(function()
        actions.close(prompt_bufnr)
        local selection = action_state.get_selected_entry()
        local pattern = ''
        if string.sub(selection.method, 1, #'*') == '*' then
          pattern = selection.method:gsub('*', selection.class .. '.')
        else
          pattern = '.' .. selection.method
        end
        utils.open_win_full(selection.path, pattern)
      end)
      return true
    end,

  }):find()
end

return M
