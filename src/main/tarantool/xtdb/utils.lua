local utils = {}

local fiber = require('fiber')
local json = require('json')

function utils.now()
  return fiber.time64()
end

function utils.to_json(data)
  return json.encode(data)
end

function utils.try(f, catch_f)
  local status, exception = pcall(f)
  if not status and catch_f then
    catch_f(exception)
  end
end

return utils
