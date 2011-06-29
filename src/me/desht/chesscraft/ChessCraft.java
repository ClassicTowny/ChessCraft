package me.desht.chesscraft;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import me.desht.chesscraft.exceptions.ChessException;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import chesspresso.Chess;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

@SuppressWarnings("serial")
public class ChessCraft extends JavaPlugin {
	
	enum Privilege { Basic, Admin };
	
	static PluginDescriptionFile description;
	static final String directory = "plugins" + File.separator + "ChessCraft";
	final Logger logger = Logger.getLogger("Minecraft");

	PermissionHandler permissionHandler;
	
	ChessPieceLibrary library;
	private final Map<String,Game> chessGames = new HashMap<String,Game>();
	private final Map<String,BoardView> chessBoards = new HashMap<String,BoardView>();
	private final Map<String,Game> currentGame = new HashMap<String,Game>();
	private final Map<String,Location> lastPos = new HashMap<String,Location>();
	
	private final ChessPlayerListener playerListener = new ChessPlayerListener(this);
	private final ChessBlockListener blockListener = new ChessBlockListener(this);
	private final ChessEntityListener entityListener = new ChessEntityListener(this);
	private final ChessCommandExecutor commandExecutor = new ChessCommandExecutor(this);
	final ChessPersistence persistence = new ChessPersistence(this);
	ExpectResponse expecter = new ExpectResponse();
	
	private static final Map<String, Object> configItems = new HashMap<String, Object>() {{
		put("broadcast_results", true);
		put("auto_delete_finished", 30);
		put("no_building", true);
		put("no_creatures", true);
		put("no_explosions", true);
	}};
	
	@Override
	public void onDisable() {
		persistence.save();
		logger.info(description.getName() + " version " + description.getVersion() + " is disabled!");
	}

	@Override
	public void onEnable() {
		description = this.getDescription();

		if (!getDataFolder().exists())
			setupDefaultStructure();
		
		configInitialise();

		setupPermissions();
		getCommand("chess").setExecutor(commandExecutor);

		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.BLOCK_DAMAGE, blockListener, Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Normal, this);
//		pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.ENTITY_EXPLODE, entityListener, Event.Priority.Normal, this);
		pm.registerEvent(Event.Type.CREATURE_SPAWN, entityListener, Event.Priority.Normal, this);
		
		library = new ChessPieceLibrary(this);
		
		persistence.reload();
		
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {	
			@Override
			public void run() {
				for (BoardView bv: listBoardViews()) {
					bv.doLighting();
				}
			}
		}, 100L, 200L);
		logger.info(description.getName() + " version " + description.getVersion() + " is enabled!" );
	}
	
	private void setupDefaultStructure() {
		log(Level.INFO, "Performing first-time setup");
		try {
			getDataFolder().mkdir();
			new File(getDataFolder(), "archive").mkdir();
			new File(getDataFolder(), "board_styles").mkdir();
			new File(getDataFolder(), "piece_styles").mkdir();
			
			extractResource("/datafiles/default-board.yml", "board_styles/Standard.yml");
			extractResource("/datafiles/default-pieces.yml", "piece_styles/Standard.yml");
		} catch (FileNotFoundException e) {
			log(Level.SEVERE, e.getMessage());
		} catch (IOException e) {
			log(Level.SEVERE, e.getMessage());
		}
	}
	
	private void extractResource(String from, String to) throws IOException {
		InputStream in = this.getClass().getResourceAsStream(from);
		if (in == null) {
			throw new IOException("can't extract resource " + from + " from plugin JAR");
		}
		File of = new File(getDataFolder(), to);
		OutputStream out = new FileOutputStream(of);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
	}

	private void setupPermissions() {
		Plugin permissionsPlugin = this.getServer().getPluginManager().getPlugin("Permissions");

		if (permissionHandler == null) {
			if (permissionsPlugin != null) {
				permissionHandler = ((Permissions) permissionsPlugin).getHandler();
				log(Level.INFO, "Permissions detected");
			} else {
				log(Level.INFO, "Permissions not detected, using ops");
			}
		}
	}

	void log(Level level, String message) {
		String logMsg = this.getDescription().getName() + ": " + message;
		logger.log(level, logMsg);
	}

	private void configInitialise() {
		Boolean saveNeeded = false;
		Configuration config = getConfiguration();
		for (String k : configItems.keySet()) {
			if (config.getProperty(k) == null) {
				saveNeeded = true;
				config.setProperty(k, configItems.get(k));
			}
		}
		if (saveNeeded) config.save();
	}
	
	boolean isAllowedTo(Player player, String node) {
		if (player == null) return true;
		// if Permissions is in force, then it overrides op status
		if (permissionHandler != null) {
			return permissionHandler.has(player, node);
		} else {
			return player.isOp();
		}
	}
	boolean isAllowedTo(Player player, String node, Privilege level) {
		if (player == null) return true;
		// if Permissions is in force, then it overrides op status
		if (permissionHandler != null) {
			return permissionHandler.has(player, node);
		} else {
			return level == Privilege.Basic ? true : player.isOp();
		}
	}
	
	void requirePerms(Player player, String node, Privilege level) throws ChessException {
		if (isAllowedTo(player, "chesscraft.admin"))
			return;
		if (isAllowedTo(player, "chesscraft.basic") && level == Privilege.Basic) 
			return;
		
		if (!isAllowedTo(player, node, level)) {
			throw new ChessException("You are not allowed to do that.");
		}
	}

	void errorMessage(Player player, String string) {
		message(player, string, ChatColor.RED, Level.WARNING);
	}

	void statusMessage(Player player, String string) {
		message(player, string, ChatColor.AQUA, Level.INFO);
	}
	
	void alertMessage(Player player, String string) {
		if (player == null) return;
		message(player, string, ChatColor.YELLOW, Level.INFO);
	}
	
	private void message(Player player, String string, ChatColor colour, Level level) {
		if (player != null) {
			player.sendMessage(colour + string);
		} else {
			log(level, string);
		}
	}

	void addBoardView(String name, BoardView view) {
		chessBoards.put(name, view);
	}
	
	void removeBoardView(String name) {
		chessBoards.remove(name);
	}
	
	Boolean checkBoardView(String name) {
		return chessBoards.containsKey(name);
	}
	
	BoardView getBoardView(String name) throws ChessException {
		if (!chessBoards.containsKey(name))
			throw new ChessException("No such board '" + name + "'");
		return chessBoards.get(name);
	}

	List<BoardView> listBoardViews() {
		SortedSet<String> sorted = new TreeSet<String>(chessBoards.keySet());
		List<BoardView> res = new ArrayList<BoardView>();
		for (String name : sorted) { res.add(chessBoards.get(name)); }
		return res;
	}
	
	public void addGame(String gameName, Game game) {
		chessGames.put(gameName, game);
	}
	
	public void removeGame(String gameName) throws ChessException {
		Game game = getGame(gameName);

		List<String>toRemove = new ArrayList<String>();
		for (String p : currentGame.keySet()) {
			if (currentGame.get(p) == game) {
				toRemove.add(p);
			}
		}
		for (String p: toRemove) {
			currentGame.remove(p);
		}
		chessGames.remove(gameName);
	}
	
	boolean checkGame(String name) {
		return chessGames.containsKey(name);
	}
	
	List<Game> listGames() {
		SortedSet<String> sorted = new TreeSet<String>(chessGames.keySet());
		List<Game> res = new ArrayList<Game>();
		for (String name : sorted) { res.add(chessGames.get(name)); }
		return res;
	}
	
	Game getGame(String name) throws ChessException {
		if (!chessGames.containsKey(name))
			throw new ChessException("No such game '" + name + "'");
		return chessGames.get(name);
	}
	
	void setCurrentGame(String playerName, String gameName) throws ChessException {
		Game game = getGame(gameName);
		setCurrentGame(playerName, game);
	}
	void setCurrentGame(String playerName, Game game) {
		currentGame.put(playerName, game);
	}
	
	Game getCurrentGame(Player player) {
		return player == null ? null : currentGame.get(player.getName());
	}
	
	Map<String,String> getCurrentGames() {
		Map<String,String> res = new HashMap<String,String>();
		for (String s : currentGame.keySet()) {
			Game game = currentGame.get(s);
			if (game != null) 
				res.put(s, game.getName());
		}
		return res;
	}
	
	Location getLastPos(Player player) {
		return lastPos.get(player.getName());
	}
	
	void setLastPos(Player player, Location loc) {
		lastPos.put(player.getName(), loc);
	}

	static String pieceToStr(int piece) {
		switch (piece) {
		case Chess.PAWN: return "pawn";
		case Chess.ROOK: return "rook";
		case Chess.KNIGHT: return "knight";
		case Chess.BISHOP: return "bishop";
		case Chess.KING: return "king";
		case Chess.QUEEN: return "queen";
		default: return "(unknown)";
		}
	}

	static MaterialWithData parseIdAndData(String string) {
		String[] items = string.split(":");
		int mat;
		byte data;
		
		if (items[0].matches("^[0-9]+$")) {
			mat = Integer.parseInt(items[0]);
		} else {
			Material m = Material.valueOf(items[0].toUpperCase());
			if (m == null) throw new IllegalArgumentException("unknown material " + items[0]);
			mat = m.getId();
		}
		if (items.length < 2) 
			return new MaterialWithData(mat, (byte)-1);
		
		if (items[1].matches("^[0-9]+$")) {
			data = Byte.parseByte(items[1]);
		} else if (mat == 35) {	// wool
			DyeColor d = DyeColor.valueOf(items[1].toUpperCase());
			if (d == null) throw new IllegalArgumentException("unknown dye colour " + items[0]);
			data = d.getData();
		} else {
			throw new IllegalArgumentException("invalid data specification " + items[1]);
		}
		return new MaterialWithData(mat, data);
	}

	static void setBlock(Block b, MaterialWithData mat) {
		if (mat.data >= 0) {
			b.setTypeIdAndData(mat.material, mat.data, false);
		} else {
			b.setTypeId(mat.material);
		}
	}
	
	static String formatLoc(Location loc) {
		String str = "<" +
			loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," +
			loc.getWorld().getName()
			+ ">";
		return str;
	}

	String getFreeBoard() throws ChessException {
		for (BoardView bv: listBoardViews()) {
			if (bv.getGame() == null)
				return bv.getName();
		}
		throw new ChessException("There are no free boards to create a game on.");
	}
	
	BoardView getBoardAt(Location loc) {
		for (BoardView bv: listBoardViews()) {
			if (bv.isPartOfBoard(loc))
				return bv;
		}
		return null;
	}
	
}
