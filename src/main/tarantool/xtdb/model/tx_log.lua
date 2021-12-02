local tx_log = {}

local utils = require('xtdb.utils')
local validator = require('xtdb.validator')

function tx_log.model(config)
  local model = {}

  --
  -- Definition
  --

  -- space
  model.SPACE_NAME = config.spaces.tx_log.name

  -- field order
  model.EVENT_OFFSET = 1
  model.TX_TIME = 2
  model.V = 3
  model.COMPACTED = 4

  -- field names
  model.EVENT_OFFSET_FIELD = 'event_offset'
  model.TX_TIME_FIELD = 'tx_time'
  model.V_FIELD = 'v'
  model.COMPACTED_FIELD = 'compacted'

  -- sequences
  model.EVENT_OFFSET_INDEX_SEQ = model.SPACE_NAME .. '_event_offset_index_seq'

  -- indexes
  model.EVENT_OFFSET_INDEX = model.SPACE_NAME .. '_event_offset_index'



  --
  -- Helpers functions
  --

  function model.get_space()
    return box.space[model.SPACE_NAME]
  end

  function model.serialize(tuple, data)
    local res = {
      event_offset = tuple[model.EVENT_OFFSET],
      tx_time = tuple[model.TX_TIME],
      v = tuple[model.V],
      compacted = tuple[model.COMPACTED]
    }

    if validator.is_some(data) then
      for k, v in pairs(data) do
        res[k] = v
      end
    end

    return res
  end



  --
  -- API
  --

  function model.submit_tx(events)
    return model.get_space():insert({
      [model.EVENT_OFFSET] = nil,
      [model.TX_TIME] = utils.now(),
      [model.V] = events,
      [model.COMPACTED] = 0
    })
  end

  return model

end

return tx_log
