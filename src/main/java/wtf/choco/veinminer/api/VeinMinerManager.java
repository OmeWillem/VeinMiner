package wtf.choco.veinminer.api;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import wtf.choco.veinminer.VeinMiner;
import wtf.choco.veinminer.data.BlockList;
import wtf.choco.veinminer.data.MaterialAlias;
import wtf.choco.veinminer.data.block.VeinBlock;
import wtf.choco.veinminer.tool.ToolCategory;
import wtf.choco.veinminer.tool.ToolTemplate;

/**
 * The central management for VeinMiner to handle everything regarding VeinMiner and its features.
 */
public class VeinMinerManager {

	private final Map<ToolCategory, ToolTemplate> toolTemplates = new EnumMap<>(ToolCategory.class);
	private final Map<ToolCategory, BlockList> blocklist = new EnumMap<>(ToolCategory.class);
	private final BlockList globalBlocklist = new BlockList();

	private final List<MaterialAlias> aliases = new ArrayList<>();
	private final Set<UUID> disabledWorlds = new HashSet<>();

	private final VeinMiner plugin;

	public VeinMinerManager(VeinMiner plugin) {
		this.plugin = plugin;
	}

	public BlockList getBlockList(ToolCategory category) {
		if (category == null) { // Yea, yea... ternary. Whatever.
			return globalBlocklist;
		}

		// Ideally it should never compute, but just in case
		return blocklist.computeIfAbsent(category, cat -> new BlockList(0));
	}

	public BlockList getBlockListGlobal() {
		return globalBlocklist;
	}

	public BlockList getAllVeinMineableBlocks() {
		BlockList[] lists = new BlockList[blocklist.size() + 1];

		int index = 0;
		for (BlockList list : blocklist.values()) {
			lists[index++] = list;
		}
		lists[index] = globalBlocklist;

		return new BlockList(lists);
	}

	public boolean isVeinMineable(BlockData data, ToolCategory category) {
		return globalBlocklist.contains(data) || blocklist.get(category).contains(data);
	}

	public boolean isVeinMineable(Material material, ToolCategory category) {
		return globalBlocklist.contains(material) || blocklist.get(category).contains(material);
	}

	public boolean isVeinMineable(BlockData data) {
		if (globalBlocklist.contains(data)) {
			return true;
		}

		for (BlockList list : blocklist.values()) {
			if (list.contains(data)) {
				return true;
			}
		}

		return false;
	}

	public boolean isVeinMineable(Material material) {
		if (globalBlocklist.contains(material)) {
			return true;
		}

		for (BlockList list : blocklist.values()) {
			if (list.contains(material)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Load all veinable blocks from the configuration file to memory.
	 */
	public void loadVeinableBlocks() {
		for (String tool : plugin.getConfig().getConfigurationSection("BlockList").getKeys(false)) {
			ToolCategory category = ToolCategory.getByName(tool);
			if (category == null && tool.equalsIgnoreCase("all")) { // Special case for "all". Error otherwise
				this.plugin.getLogger().warning("Attempted to create blocklist for the non-existent category, " + tool + "... ignoring.");
				continue;
			}

			BlockList blocklist = getBlockList(category);
			List<String> blocks = plugin.getConfig().getStringList("BlockList." + tool);

			for (String value : blocks) {
				// Material information
				BlockData data;
				boolean specificData = value.endsWith("]");

				try {
					data = Bukkit.createBlockData(value);
				} catch (IllegalArgumentException e) {
					this.plugin.getLogger().warning("Unknown block type (was it an item?) and/or block states. " + value);
					continue;
				}

				if (specificData) { // Specific data
					blocklist.add(data, value);
				} else { // Wildcard
					blocklist.add(data.getMaterial());
				}
			}
		}
	}

	public void loadToolTemplates() {
		this.toolTemplates.clear();

		FileConfiguration config = plugin.getConfig();
		for (String categoryName : config.getConfigurationSection("Tools").getKeys(false)) {
			ToolCategory category = ToolCategory.getByName(categoryName);

			ConfigurationSection categoryTemplate = config.getConfigurationSection("Tools." + categoryName + "Tool");
			if (categoryTemplate == null) continue;

			Material type = Material.matchMaterial(categoryTemplate.getString("Type"));
			String name = ChatColor.translateAlternateColorCodes('&', categoryTemplate.getString("Name", ""));
			List<String> lore = categoryTemplate.getStringList("Lore").stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList());

			ToolTemplate template = null;
			if (type != null) {
				if (!category.contains(type)) {
					this.plugin.getLogger().warning("Invalid material type " + type.getKey() + " for category " + category.getName() + ". Ignoring...");
					continue;
				}

				template = new ToolTemplate(type, (name.isEmpty()) ? null : name, (lore.isEmpty()) ? null : lore);
			} else {
				template = new ToolTemplate(category, (name.isEmpty()) ? null : name, (lore.isEmpty()) ? null : lore);
			}

			this.toolTemplates.put(category, template);
		}
	}

	public void setToolTemplate(ToolCategory category, ToolTemplate template) {
		Preconditions.checkArgument(category != null, "Tool category must not be null");
		Preconditions.checkArgument(category.canHaveToolTemplate(), "The provided category (%s) cannot define a tool template", category.getName());

		this.toolTemplates.put(category, template);
	}

	public ToolTemplate getToolTemplate(ToolCategory category) {
		return toolTemplates.computeIfAbsent(category, ToolTemplate::empty);
	}

	/**
	 * Load all disabled worlds from the configuration file to memory.
	 */
	public void loadDisabledWorlds() {
		this.disabledWorlds.clear();

		for (String worldName : plugin.getConfig().getStringList("DisabledWorlds")) {
			World world = Bukkit.getWorld(worldName);

			if (world == null) {
				this.plugin.getLogger().info("Unknown world found... \"" + worldName + "\". Ignoring...");
				continue;
			}

			this.disabledWorlds.add(world.getUID());
		}
	}

	/**
	 * Check whether a world has VeinMiner disabled or not.
	 *
	 * @param world the world to check
	 *
	 * @return true if the world has VeinMiner disabled, false otherwise
	 */
	public boolean isDisabledInWorld(World world) {
		Preconditions.checkNotNull(world, "Cannot check state of veinminer in null world");
		return disabledWorlds.contains(world.getUID());
	}

	/**
	 * Get a set of all worlds in which VeinMiner is disabled. A copy of the set is returned,
	 * therefore any changes made to the returned set will not affect the disabled worlds.
	 *
	 * @return a set of all disabled worlds
	 */
	public Set<World> getDisabledWorlds() {
		return disabledWorlds.stream().map(w -> Bukkit.getWorld(w)).collect(Collectors.toSet());
	}

	/**
	 * Disable vein miner in a specific world.
	 *
	 * @param world the world for which to disable VeinMiner
	 */
	public void setDisabledInWorld(World world) {
		Preconditions.checkNotNull(world, "Cannot disable veinminer in null world");
		this.disabledWorlds.add(world.getUID());
	}

	/**
	 * Enable VeinMiner in a specific world.
	 *
	 * @param world the world for which to enabled VeinMiner
	 */
	public void setEnabledInWorld(World world) {
		Preconditions.checkNotNull(world, "Cannot enable veinminer in null world");
		this.disabledWorlds.remove(world.getUID());
	}

	/**
	 * Clear all worlds from the blacklist.
	 */
	public void clearDisabledWorlds() {
		this.disabledWorlds.clear();
	}

	/**
	 * Register a new MaterialAlias.
	 *
	 * @param alias the alias to register
	 */
	public void registerAlias(MaterialAlias alias) {
		Preconditions.checkNotNull(alias, "Cannot register a null alias");
		this.aliases.add(alias);
	}

	/**
	 * Unregister a MaterialAlias.
	 *
	 * @param alias the alias to unregister
	 */
	public void unregisterAlias(MaterialAlias alias) {
		this.aliases.remove(alias);
	}

	/**
	 * Get the alias associated with a specific block data.
	 *
	 * @param data the block data to reference
	 *
	 * @return the associated alias. null if none
	 */
	public MaterialAlias getAliasFor(BlockData data) {
		return aliases.stream().filter(a -> a.isAliased(data)).findFirst().orElse(null);
	}

	/**
	 * Get the alias associated with a specific material.
	 *
	 * @param material the material to reference
	 *
	 * @return the associated alias. null if none
	 */
	public MaterialAlias getAliasFor(Material material) {
		return aliases.stream().filter(a -> a.isAliased(material)).findFirst().orElse(null);
	}

	/**
	 * Load all material aliases from config to memory.
	 */
	public void loadMaterialAliases() {
		this.aliases.clear();

		for (String aliasList : plugin.getConfig().getStringList("Aliases")) {
			MaterialAlias alias = new MaterialAlias();

			for (String aliasMaterial : aliasList.split("\\s*,\\s*")) {
				// Material information
				BlockData data;
				boolean specificData = aliasMaterial.endsWith("]");

				try {
					data = Bukkit.createBlockData(aliasMaterial);
				} catch (IllegalArgumentException e) {
					this.plugin.getLogger().warning("Unknown block type (was it an item?) and/or block states. " + aliasMaterial);
					continue;
				}

				alias.addAlias((specificData) ? VeinBlock.get(data, aliasMaterial) : VeinBlock.get(data.getMaterial()));
			}

			this.aliases.add(alias);
		}
	}

	/**
	 * Clear all localised data in the VeinMiner Manager.
	 */
	public void clearLocalisedData() {
		this.toolTemplates.clear();
		this.blocklist.values().forEach(BlockList::clear);
		this.blocklist.clear();
		this.globalBlocklist.clear();

		this.disabledWorlds.clear();
		this.aliases.clear();
	}

}