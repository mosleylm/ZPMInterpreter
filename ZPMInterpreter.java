import java.awt.List;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

public class ZPMInterpreter {
		private static Map<String, Integer> intValues = new HashMap<String, Integer>();
		private static Map<String, String> stringValues = new HashMap<String, String>();
		private static Map<String, String[]> storedProc = new HashMap<String, String[]>();
		static boolean error;
		
		public static void main(String[] args) {
			final long startTime = System.currentTimeMillis();
			
			if(args.length == 1) {
				String zFileContents = readFile(args[0]);
				interpreter(zFileContents);
			} else {
				System.out.println("Pass file name as argument");
			}
			
			final long endTime = System.currentTimeMillis();
			System.out.println("Total execution time: " + (endTime - startTime) + "ms");
		}
		
		private static void interpreter(String zCode) {
			Scanner scanner = new Scanner(zCode);
			int lineNum = 0;
			boolean inStored = false;
			error = false;
			
			// read z+- line by line
			while(scanner.hasNextLine() && !error) {
				String[] tokens = scanner.nextLine().split("\\s+");
				
				// skip blank lines
				if(tokens.length > 1) {
					inStored = interpretLine(tokens, lineNum, inStored);
				}
				
				
				if(inStored) {
					String[] procTokens = scanner.nextLine().split("\\s+");
					
					while(scanner.hasNextLine() && inStored) {
						String[] toAdd = scanner.nextLine().split("\\s+");
						
						if(toAdd[0].equals("ENDPROC")) {
							inStored = false;
						}
						else {
							Object[] procArrays = addArrays(procTokens, toAdd);
							procTokens = Arrays.asList(procArrays).toArray(new String[procArrays.length]);
						}
						lineNum++;
					}
					
					storedProc.put(tokens[1], procTokens);
				}
				
				lineNum++;
			}
			
			scanner.close();
		}
		
		private static Object[] addArrays(String[] arr1, String[] arr2) {
			ArrayList<String> list = new ArrayList<String>(Arrays.asList(arr1));
			list.addAll(Arrays.asList(arr2));
			
			Object[] arr = list.toArray();
			
			return arr;
		}
		
		private static boolean interpretLine(String[] tokens, int lineNum, boolean inStored) {
			
			// check first token and pass tokens off
			if(tokens[0].equals("PRINT")) {
				printStmt(tokens, lineNum);
			}
			else if(tokens[0].equals("FOR")) {
				forLoop(tokens, lineNum);
			}
			else if(tokens[0].equals("PROC")) {
				inStored = true;
			}
			else if(tokens[0].equals("CALL")) {
				performStored(tokens[1], lineNum);
			}
			else {
				assignmentOp(tokens, lineNum);
			}
			
			return inStored;
		}
		
		private static void performStored(String key, int lineNum) {
			String storedLines = reformat(storedProc.get(key));
			Scanner scanner = new Scanner(storedLines);
			
			while(scanner.hasNextLine()) {
				String[] tokens = scanner.nextLine().split("\\s+");
				boolean doTask = interpretLine(tokens, lineNum, false);
			}
		}
		
		private static void forLoop(String[] tokens, int lineNum) {
			int numLoops = Integer.parseInt(tokens[1]);
			// chops off FOR X and ENDFOR for a given for loop
			String[] toLoop = Arrays.copyOfRange(tokens, 2, tokens.length - 1);
			
			// reformat for-loop into individual lines and re-parse
			String loopString = reformat(toLoop);
			
			
			for(int i = 0; i < numLoops; i++) {
				Scanner scanner = new Scanner(loopString);
				
				while(scanner.hasNextLine()) {
					String[] newTokens = scanner.nextLine().split("\\s+");
					boolean doTask = interpretLine(newTokens, lineNum, false);
				}
				scanner.close();
			}
		}
		
		private static String reformat(String[] tokens) {
			String forString = "";
			
			for(int i = 0; i < tokens.length; i++) {
				if(tokens[i].equals(";")) {
					forString += tokens[i];
					forString += "\n";
				}
				else if(tokens[i].equals("FOR")) {
					int forStack = 1;
					forString += tokens[i];
					forString += " ";
					i++;
					
					// Adds a for loop to a line
					while(i < tokens.length && forStack > 0) {
						if(tokens[i].equals("ENDFOR")) {
							forStack--;
							forString += tokens[i];
							forString += " ";
						}
						else if(tokens[i].equals("FOR")) {
							forStack++;
							forString += tokens[i];
							forString += " ";
						}
						else {
							forString += tokens[i];
							forString += " ";
						}
						i++;
						
					}
					forString += "\n";

					if(i < tokens.length && forStack == 0) {
						forString += tokens[i];
						forString += " ";
					}
				}
				else {
					forString += tokens[i];
					forString += " ";
				}
			}
			
			return forString;
		}
		
		private static void addition(String[] tokens, int lineNum) {
			String varToOp = tokens[0];
			
			if(intValues.containsKey(varToOp)) {
				if(intValues.containsKey(tokens[2])) {
					int tmp = intValues.get(tokens[2]);
					tmp += intValues.get(varToOp);
					intValues.put(varToOp, tmp);
				}
				else {
					if(stringValues.containsKey(tokens[2])) {
						printErr(lineNum);
					}
					else {
						try {
							int tmp = Integer.parseInt(tokens[2]);
							tmp += intValues.get(varToOp);
							intValues.put(varToOp, tmp);
							
						} catch(NumberFormatException e) {
							printErr(lineNum);
						}
					}
				}
			} 
			else if(stringValues.containsKey(varToOp)) {
				if(stringValues.containsKey(tokens[2])) {
					String temp = stringValues.get(varToOp);
					temp += stringValues.get(tokens[2]);
					stringValues.put(varToOp, temp);
				}
				else {
					if(intValues.containsKey(tokens[2])) {
						printErr(lineNum);
					}
					else {
						if(tokens[2].charAt(0) == '\"') {
							String temp = stringValues.get(varToOp);
							temp += stringValues.get(tokens[2].replace("\"", ""));
							stringValues.put(varToOp, temp);
						}
						else {
							printErr(lineNum);
						}
					}
				}
			}
			else {
				printErr(lineNum);
			}
		}
		
		private static void subtraction(String[] tokens, int lineNum) {
			String varToOp = tokens[0];
			
			if(intValues.containsKey(varToOp)) {
				if(intValues.containsKey(tokens[2])) {
					int tmp = intValues.get(tokens[2]);
					tmp = intValues.get(varToOp) - tmp;
					intValues.put(varToOp, tmp);
				}
				else {
					if(stringValues.containsKey(tokens[2])) {
						printErr(lineNum);
					}
					else {
						try {
							int tmp = Integer.parseInt(tokens[2]);
							tmp = intValues.get(varToOp) - tmp;
							intValues.put(varToOp, tmp);
						} catch(NumberFormatException e) {
							printErr(lineNum);
						}
					}
				}
			} 
			else {
				printErr(lineNum);
			}
		}
		
		private static void multiplication(String[] tokens, int lineNum) {
			String varToOp = tokens[0];
			
			if(intValues.containsKey(varToOp)) {
				if(intValues.containsKey(tokens[2])) {
					int tmp = intValues.get(tokens[2]);
					tmp *= intValues.get(varToOp);
					intValues.put(varToOp, tmp);
				}
				else {
					if(stringValues.containsKey(tokens[2])) {
						printErr(lineNum);
					}
					else {
						try {
							int tmp = Integer.parseInt(tokens[2]);
							tmp *= intValues.get(varToOp);
							intValues.put(varToOp, tmp);
						} catch(NumberFormatException e) {
							printErr(lineNum);
						}
					}
				}
			} 
			else {
				printErr(lineNum);
			}
		}
		
		private static void assignmentOp(String[] tokens, int lineNum) {
			String varName = tokens[0];
			
			if(tokens[1].equals("=")) {
				
				if(tokens[2].charAt(0) == '\"') {
					if(intValues.containsKey(varName)) {
						intValues.remove(varName);
					}
					
					stringValues.put(varName, tokens[2].replace("\"", ""));
				}
				else if(intValues.containsKey(tokens[2])) {
					if(stringValues.containsKey(varName)) {
						stringValues.remove(varName);
					}
					
					intValues.put(varName, intValues.get(tokens[2]));
				}
				else if(stringValues.containsKey(tokens[2])) {
					if(intValues.containsKey(varName)) {
						intValues.remove(varName);
					}
					
					stringValues.put(varName, stringValues.get(tokens[2]));
				}
				else {
					if(stringValues.containsKey(varName)) {
						stringValues.remove(varName);
					}
					
					try {
						intValues.put(varName, Integer.parseInt(tokens[2]));
					} catch(NumberFormatException e) {
						printErr(lineNum);
					}
				}
			}
			else if(tokens[1].equals("-=")) {
				subtraction(tokens, lineNum);
			}
			else if(tokens[1].equals("+=")) {
				addition(tokens, lineNum);
			}
			else if(tokens[1].equals("*=")) {
				multiplication(tokens, lineNum);
			}
		}
		
		private static void printStmt(String[] tokens, int lineNum) {
			if(intValues.containsKey(tokens[1])) {
				System.out.println(tokens[1] + "=" + intValues.get(tokens[1]));
			}
			else if(stringValues.containsKey(tokens[1])) {
				System.out.println(tokens[1] + "=" + stringValues.get(tokens[1]));
			}
			else {
				printErr(lineNum);
			}
		}
		
		private static void printErr(int lineNum) {
			error = true;
			lineNum++;
			System.out.println("RUNTIME ERROR: line " + lineNum);
		}
		
		private static String readFile(String filePath) {
			try {
				FileInputStream fileStream = new FileInputStream(filePath);
				
				try {
					InputStreamReader in = new InputStreamReader(fileStream, Charset.defaultCharset());
					Reader reader = new BufferedReader(in);
					StringBuilder build = new StringBuilder();
					char[] buffer = new char[8192];
					int read;
					
					while((read = reader.read(buffer, 0, buffer.length)) > 0) {
						build.append(buffer, 0, read);
					}
					
					build.append("\n");
					return build.toString();
				} finally {
					fileStream.close();
				}
			} catch(IOException e) {
				System.out.println(e);
				return null;
			}
		}
}
