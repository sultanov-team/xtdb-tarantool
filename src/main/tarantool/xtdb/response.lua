local response = {}

local error = require('xtdb.error')

function response.error(code)
  local message = { [code] = error.CODES[code] }
  return false, message
end

function response.ok(data)
  return true, data
end

return response
