local validator = {}

local log = require('log')

local config_default_values = {}

local config_default_space_names = {
  tx_log = 'xtdb_tx_log',
  kv_store = 'xtdb_kv_store',
  document_store = 'xtdb_document_store',
}

function validator.is_nil(x)
  return x == nil
end

function validator.is_some(x)
  return x ~= nil
end

function validator.is_string(str)
  return type(str) == 'string'
end

function validator.not_blank_string(str)
  return validator.is_string(str) and str ~= ''
end

function validator.is_number(x)
  return type(x) == 'number'
end

function validator.is_pos(x)
  return validator.is_number(x) and x >= 0
end

function validator.is_table(tbl)
  return type(tbl) == 'table'
end

function validator.validate(config)
  if not validator.is_table(config) then
    config = {}
    log.warn('Config is not a table. Use the defaults values instead.')
  end

  for param_name, default_value in pairs(config_default_values) do
    param_value = config[param_name]
    if validator.is_nil(param_value) or not validator.is_pos(param_value) then
      config[param_name] = default_value
      log.warn('Use %s for %s', default_value, param_name)
    end
  end

  if not validator.is_table(config.spaces) then
    config.spaces = {}
  end

  for param_name, default_value in pairs(config_default_space_names) do
    if not (validator.is_table(config.spaces[param_name]) and validator.not_blank_string(config.spaces[param_name].name)) then
      config.spaces[param_name] = {}
      config.spaces[param_name].name = default_value
    end
  end

  return config

end

return validator
