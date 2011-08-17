package me.desht.chesscraft.results;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import me.desht.chesscraft.Game;
import me.desht.chesscraft.enums.GameResult;
import me.desht.chesscraft.enums.GameState;
import me.desht.chesscraft.exceptions.ChessException;
import me.desht.chesscraft.log.ChessCraftLogger;

public class Results {
	private static Results results = null;	// singleton class
	private ResultsDB db;
	private final List<ResultEntry> entries = new ArrayList<ResultEntry>();
	private final Map<String, ResultViewBase> views = new HashMap<String, ResultViewBase>();

	private Results() {
		
	}

	private void registerView(String viewName, ResultViewBase view) {
		views.put(viewName, view);
	}
	
	public static Results getResultsHandler() {
		if (results == null) {
			results = new Results();
			results.db = new ResultsDB();
			results.loadEntries();
			results.registerView("ladder", new Ladder());
			results.registerView("league", new League());
		}
		return results;
	}

	ResultsDB getDB() {
		return db;
	}

	public ResultViewBase getView(String viewName) throws ChessException {
		if (!views.containsKey(viewName)) {
			throw new ChessException("No such results view " + viewName);
		}
		return views.get(viewName);
	}
	
	public List<ResultEntry> getEntries() {
		return entries;
	}

	public Connection getConnection() {
		return db.getConnection();
	}

	public void logResult(Game game, GameResult rt) {
		if (game.getState() != GameState.FINISHED) {
			return;
		}
	
		ResultEntry re = new ResultEntry(game, rt);
		logResult(re);
	}
	
	public void logResult(ResultEntry re) {
		entries.add(re);
		re.save(getConnection());
		for (ResultViewBase view : views.values()) {
			view.addResult(re);
		}
	}
	
	/**
	 * Get the number of wins for a player
	 * 
	 * @param playerName	The player to check
	 * @return	The number of games this player has won
	 */
	public int getWins(String playerName) {
		int nWins = 0;
		
		try {
			PreparedStatement stmtW = getConnection().prepareStatement(
					"SELECT COUNT(playerWhite) FROM results WHERE " +
					"pgnResult = '1-0' AND playerWhite = ?");
			PreparedStatement stmtB = getConnection().prepareStatement(
					"SELECT COUNT(playerBlack) FROM results WHERE " +
					"pgnResult = '0-1' AND playerBlack = ?");
			return doSearch(playerName, stmtW, stmtB);
		} catch (SQLException e) {
			ChessCraftLogger.warning("SQL query failed: " + e.getMessage());
		}
		
		return nWins;
	}

	/**
	 * Get the number of draws for a player
	 * 
	 * @param playerName	The player to check
	 * @return	The number of games this player has drawn
	 */
	public int getDraws(String playerName) {
		int nDraws = 0;
		
		try {
			PreparedStatement stmtW = getConnection().prepareStatement(
					"SELECT COUNT(playerWhite) FROM results WHERE " +
					"pgnResult = '1/2-1/2' AND playerWhite = ?");
			PreparedStatement stmtB = getConnection().prepareStatement(
					"SELECT COUNT(playerBlack) FROM results WHERE " +
					"pgnResult = '1/2-1/2' AND playerBlack = ?");
			return doSearch(playerName, stmtW, stmtB);
		} catch (SQLException e) {
			ChessCraftLogger.warning("SQL query failed: " + e.getMessage());
		}
		
		return nDraws;
	}
	
	/**
	 * Get the number of losses for a player
	 * 
	 * @param playerName	The player to check
	 * @return	The number of games this player has lost
	 */
	public int getLosses(String playerName) {
		try {
			PreparedStatement stmtW = getConnection().prepareStatement(
					"SELECT COUNT(playerWhite) FROM results WHERE " +
					"pgnResult = '0-1' AND playerWhite = ?");
			PreparedStatement stmtB = getConnection().prepareStatement(
					"SELECT COUNT(playerBlack) FROM results WHERE " +
					"pgnResult = '1-0' AND playerBlack = ?");
			return doSearch(playerName, stmtW, stmtB);
		} catch (SQLException e) {
			ChessCraftLogger.warning("SQL query failed: " + e.getMessage());
			return 0;
		}
	}
	
	/**
	 * Return the league table score for a player.  A win count as 3 points,
	 * a draw as 1 point, a loss as 0 points.
	 * 
	 * @param playerName	The player to check
	 * @return	The player's total score
	 */
	public int getScore(String playerName) {
		return getWins(playerName) * 3 + getDraws(playerName);
	}

	private int doSearch(String playerName, PreparedStatement stmtW, PreparedStatement stmtB) throws SQLException {
		int count = 0;
		ResultSet rs = stmtW.executeQuery(playerName);
		count += rs.getInt(1);
		rs = stmtB.executeQuery(playerName);
		count += rs.getInt(1);
		return count;
	}
	
	
	private void loadEntries() {
		try {
			Statement stmt = getConnection().createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM results");
			while (rs.next()) {
				ResultEntry e = new ResultEntry(rs);
				entries.add(e);
			}
		} catch (SQLException e) {
			ChessCraftLogger.warning("SQL query failed: " + e.getMessage());
		}
	}
	
	public void addTestData() {
		final int N_PLAYERS = 4;
		final int N_GAMES = 20;
		String[] pgnResults = { "1-0", "0-1", "1/2-1/2" };
		
		try {
			getConnection().setAutoCommit(false);
			Statement clear = getConnection().createStatement();
			clear.executeUpdate("DELETE FROM results WHERE playerWhite LIKE 'testplayer%' OR playerBlack LIKE 'testplayer%'");
			Random rnd = new Random();
			for (int i = 0; i < N_GAMES; i++) {
				String plw = "testplayer" + rnd.nextInt(N_PLAYERS);
				String plb = "testplayer" + rnd.nextInt(N_PLAYERS);
				if (plw.equals(plb)) {
					continue;
				}
				String gn = "testgame" + i;
				long start = System.currentTimeMillis() - 5000;
				long end = System.currentTimeMillis() - 4000;
				String pgnRes = pgnResults[rnd.nextInt(pgnResults.length)];
				GameResult rt;
				if (pgnRes.equals("1-0") || pgnRes.equals("0-1")) {
					rt = GameResult.Checkmate;
				} else {
					rt = GameResult.DrawAgreed;	
				}
				ResultEntry re = new ResultEntry(plw, plb, gn, start, end, pgnRes, rt);
				entries.add(re);
				re.save(getConnection());
			}
			getConnection().setAutoCommit(true);
			for (ResultViewBase view : views.values()) {
				view.rebuild();
			}
			System.out.println("test data added & committed");
		} catch (SQLException e) {
			ChessCraftLogger.warning("can't put test data into DB: " + e.getMessage());
		}
	}

	public void shutdown() {
		db.shutdown();
	}
}