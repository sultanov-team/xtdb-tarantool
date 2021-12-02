local migrator = {}

function migrator.migrate(config)

  if box.cfg.read_only == false then
    box.once('20211202_xtdb_example_migration', function()
      -- put migrations with box.once here
    end)
  end

end

return migrator
