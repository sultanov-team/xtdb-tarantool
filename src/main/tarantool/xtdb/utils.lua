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

function utils.map(f, coll)
  local res = {}
  for idx, x in pairs(coll) do
    res[idx] = f(x)
  end
  return res
end

return utils
