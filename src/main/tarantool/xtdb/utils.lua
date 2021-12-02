local utils = {}

local fiber = require('fiber')
local json = require('json')

function utils.now()
  return fiber.time64()
end

function utils.to_json(data)
  return json.encode(data)
end

return utils
