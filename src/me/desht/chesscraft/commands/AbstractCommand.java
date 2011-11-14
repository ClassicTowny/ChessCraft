package me.desht.chesscraft.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import me.desht.chesscraft.exceptions.ChessException;
import me.desht.chesscraft.util.ChessUtils;
import me.desht.chesscraft.ChessCraft;

public abstract class AbstractCommand {
	private String command;	
	private String subCommands[];
	private String usage[];
	private int minArgs, maxArgs;
	private String permissionNode;
	private boolean quotedArgs;
	
	public AbstractCommand(String label) {
		this(label, 0, Integer.MAX_VALUE);
	}

	public AbstractCommand(String label, int minArgs) {
		this(label, minArgs, Integer.MAX_VALUE);
	}
	
	public AbstractCommand(String label, int minArgs, int maxArgs) {
		quotedArgs = false;
		String[] fields = label.split(" ");
		this.command = fields[0];
		this.subCommands = new String[fields.length - 1];
		for (int i = 1; i < fields.length; i++) {
			subCommands[i - 1] = fields[i];
		}
		
		this.minArgs = minArgs;
		this.maxArgs = maxArgs;
	}

	public boolean matchesSubCommand(String label, String[] args) {
		if (!label.equalsIgnoreCase(this.command))
			return false;
		if (args.length < subCommands.length)
			return false;
		
		for (int i = 0; i < subCommands.length; i++) {
//			System.out.println(String.format("match subcmd %d: %s", i, subCommands[i]));
			if (!partialMatch(args[i], subCommands[i])) {
				return false;
			}
		}
		
		return true;
	}
	
	public boolean matchesArgCount(String label, String args[]) {
		if (!label.equalsIgnoreCase(this.command))
			return false;

		int nArgs;
		if (isQuotedArgs()) {
			List<String> a = ChessUtils.splitQuotedString(combine(args, 0));
			nArgs = a.size() - subCommands.length;
		} else {
			nArgs = args.length - subCommands.length;
		}
//		System.out.println(String.format("match %s, nArgs=%d min=%d max=%d", label, nArgs, minArgs, maxArgs));
		
		return nArgs >= minArgs && nArgs <= maxArgs;
	}
	
	String[] getArgs(String[] args) {
		String[] result = new String[args.length - subCommands.length];
		for (int i = subCommands.length; i < args.length; i++) {
			result[i - subCommands.length] = args[i];
		}
		if (isQuotedArgs()) {
			List<String>a = ChessUtils.splitQuotedString(combine(result, 0));
			return a.toArray(new String[a.size()]);
		} else {
			return result;
		}
	}
	
	protected void setPermissionNode(String node) {
		this.permissionNode = node;
	}
	
	protected void setUsage(String usage) {
		this.usage = new String[] { usage };
	}
	
	protected void setUsage(String[] usage) {
		this.usage = usage;
	}
	
	protected String[] getUsage() {
		return usage;
	}

	protected String getPermissionNode() {
		return permissionNode;
	}
	
	public boolean isQuotedArgs() {
		return quotedArgs;
	}

	public void setQuotedArgs(boolean usesQuotedArgs) {
		this.quotedArgs = usesQuotedArgs;
	}

	protected void notFromConsole(Player player) throws ChessException {
		if (player == null) {
			throw new ChessException("This command can't be run from the console.");
		}	
	}
	
	static boolean partialMatch(String[] args, int index, String match) {
		if (index >= args.length) {
			return false;
		}
		return partialMatch(args[index], match);
	}

	static Boolean partialMatch(String str, String match) {
		int l = match.length();
		if (str.length() < l) return false;
		return str.substring(0, l).equalsIgnoreCase(match);
	}
	
	static String combine(String[] args, int idx) {
		return combine(args, idx, args.length - 1);
	}
	
	static String combine(String[] args, int idx1, int idx2) {
		StringBuilder result = new StringBuilder();
		for (int i = idx1; i <= idx2 && i < args.length; i++) {
			result.append(args[i]);
			if (i < idx2) {
				result.append(" ");
			}
		}
		return result.toString();
	}
	
	static Map<String, String> parseCommandOptions(String[] args, int start) {
		Map<String, String> res = new HashMap<String, String>();

		Pattern pattern = Pattern.compile("^-(.+)"); //$NON-NLS-1$

		for (int i = start; i < args.length; ++i) {
			Matcher matcher = pattern.matcher(args[i]);
			if (matcher.find()) {
				String opt = matcher.group(1);
				try {
					res.put(opt, args[++i]);
				} catch (ArrayIndexOutOfBoundsException e) {
					res.put(opt, null);
				}
			}
		}
		return res;
	}
	
	public abstract boolean execute(ChessCraft plugin, Player player, String[] args) throws ChessException;

	void showUsage(Player player) {
		if (usage != null) {
			for (int i = 0; i < usage.length; i++) {
				if (i == 0) {
					ChessUtils.errorMessage(player, "Usage: " + usage[i]);
				} else {
					ChessUtils.errorMessage(player, "         " + usage[i]);
				}
			}
		}
	}
}