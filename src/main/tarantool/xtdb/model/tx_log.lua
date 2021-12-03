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
  model.TX_ID = 1
  model.TX_TIME = 2
  model.TX_EVENTS = 3

  -- field names
  model.TX_ID_FIELD = 'tx_id'
  model.TX_TIME_FIELD = 'tx_time'
  model.TX_EVENTS_FIELD = 'tx_events'

  -- sequences
  model.TX_ID_INDEX_SEQ = model.SPACE_NAME .. '_tx_id_index_seq'

  -- indexes
  model.TX_ID_INDEX = model.SPACE_NAME .. '_tx_id_index'



  --
  -- Helper functions
  --

  function model.get_space()
    return box.space[model.SPACE_NAME]
  end




  --
  -- API
  --

  function model.submit_tx(tx_events)
    return model.get_space():insert({
      [model.TX_ID] = nil,
      [model.TX_TIME] = utils.now(),
      [model.TX_EVENTS] = tx_events
    })
  end

  function model.open_tx_log(after_tx_id)
    return model.get_space():select({ [model.TX_ID] = after_tx_id }, 'GT') -- FIXME: add limits?
  end

  function model.latest_submitted_tx()
    local res
    utils.try(function()
      res = { [model.TX_ID] = box.sequence[model.TX_ID_INDEX_SEQ]:current() }
    end)
    return res
  end

  return model

end

return tx_log
