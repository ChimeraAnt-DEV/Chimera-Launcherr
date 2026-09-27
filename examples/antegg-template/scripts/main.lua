-- Example .AntEgg script entry point.
--
-- The launcher runs this file with the embedded Lua VM when the mod loads. The `mod` global
-- carries the manifest's name/version/author/id and the absolute paths of the extracted mod
-- directory (`mod.dir`) and its writable data folder (`mod.sandbox`).
--
-- Keep setup work here. Per-frame gameplay logic is not the intended use of a script mod: the
-- embedded VM is an interpreter, not a JIT, so anything in a hot loop costs frames.

local function describe()
    local lines = {
        "loaded " .. mod.name .. " v" .. mod.version .. " by " .. mod.author,
        "id: " .. mod.id,
        "dir: " .. mod.dir,
        "sandbox: " .. mod.sandbox,
    }
    return table.concat(lines, "\n")
end

log = log or print
log(describe())

-- Dependencies are declared in egg.json; by the time this runs they are present, so it is safe
-- to assume their data files exist under their own sandbox directories.
for i = 1, #mod.dependencies do
    log("depends on: " .. mod.dependencies[i])
end
