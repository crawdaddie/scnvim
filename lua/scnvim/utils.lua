--- Utility functions.
---@module scnvim.utils

local M = {}
local _path = require 'scnvim.path'
-- local pickers = require 'telescope.pickers'

--- Returns the content of a lua file on disk
---@param path The path to the file to load
function M.load_file(path)
  -- this check is here because loadfile will read from stdin if nil
  if not path then
    error '[scnvim] no path to read'
  end
  local func, err = loadfile(path)
  if not func then
    error(err)
  end
  local ok, content = pcall(func)
  if not ok then
    error(content)
  end
  return content
end

--- Match an exact occurence of word
--- (replacement for \b word boundary)
---@param input The input string
---@param word The word to match
---@return True if word matches, otherwise false
function M.str_match_exact(input, word)
  return string.find(input, '%f[%a]' .. word .. '%f[%A]') ~= nil
end

--- Print a highlighted message to the command line.
---@param message The message to print.
---@param hlgroup The highlight group to use. Default = ErrorMsg
function M.print(message, hlgroup)
  local expr = string.format('echohl %s | echom "[scnvim] " . "%s" | echohl None', hlgroup or 'ErrorMsg', message)
  vim.cmd(expr)
end

--- Get the content of the auto generated snippet file.
---@return A table with the snippets.
function M.get_snippets()
  return M.load_file(_path.get_asset 'snippets')
end

-- function M.open_picker()
--   local opts = {}
--   pickers.new(opts, {
--     prompt_title = 'sc methods',
--   }):find()
-- end

function M.open_win(uri, pattern)
  buf = vim.api.nvim_create_buf(false, true)

  vim.api.nvim_buf_set_option(buf, "bufhidden", "wipe")


  local r, c = unpack(vim.api.nvim_win_get_cursor(0))
  local width = vim.api.nvim_get_option("columns")
  local height = vim.api.nvim_get_option("lines")
  print('cursor ' .. r .. ' ' .. c .. ' ' .. width .. ' ' .. height)

  -- local win_height = math.ceil(height * 0.8 - 4)
  -- local win_width = math.ceil(width * 0.8)
  -- local win_height = 64
  local win_width = 96
  local win_height = math.ceil(math.min(c + 64, height * 0.8 - 4) - c)

  local row = math.ceil((height - win_height) / 2 - 1)
  local col = math.ceil((width - win_width) / 2)

  local opts = {
    style = "minimal",
    relative = "cursor",
    width = win_width,
    height = win_height,
    row = 1,
    col = 0,
    border = "rounded",
  }

  win = vim.api.nvim_open_win(buf, true, opts)
  vim.api.nvim_win_set_option(win, "cursorline", true)

  local expr = string.format('edit %s', uri)
  if pattern then
    expr = string.format('edit +/%s %s', pattern, uri)
  end

  vim.cmd(expr)
  return win
end

function M.open_win_full(uri, pattern)
  buf = vim.api.nvim_create_buf(false, true)

  vim.api.nvim_buf_set_option(buf, "bufhidden", "wipe")


  local r, c = unpack(vim.api.nvim_win_get_cursor(0))
  local width = vim.api.nvim_get_option("columns")
  local height = vim.api.nvim_get_option("lines")
  -- print('cursor ' .. r .. ' ' .. c .. ' ' .. width .. ' ' .. height)

  local win_height = math.ceil(height * 0.8 - 4)
  local win_width = math.ceil(width * 0.8)
  -- local win_height = 64
  -- local win_width = 96
  -- local win_height = math.ceil(math.min(c + 64, height * 0.8 - 4) - c)

  local row = math.ceil((height - win_height) / 2 - 1)
  local col = math.ceil((width - win_width) / 2)

  local opts = {
    style = "minimal",
    relative = "editor",
    width = win_width,
    height = win_height,
    row = row,
    col = col,
    border = "rounded",
  }

  win = vim.api.nvim_open_win(buf, true, opts)
  vim.api.nvim_win_set_option(win, "cursorline", true)

  local expr = string.format('edit %s', uri)
  if pattern then
    expr = string.format('edit +/%s %s', pattern, uri)
  end

  vim.cmd(expr)
  return win
end

return M
