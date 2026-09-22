--[[
countIcons function
by @supermamon (github.com/supermamon/cylinder-scripts/)

Returns the correct number of icons on the page/folder
need to find another way without the loop

v1.0 2014-02-19: First release.
]]
return function(page)
	local i = 0
	while true do
		i = i + 1
		if page[i] == nil then break end
	end
	return (i-1)
end