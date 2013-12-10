import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import dalvik.system.DexClassLoader;


public class DexWare {

	private static int PORT = 8085;
	private static int MAX_THREADS = 10;
	private static int MAX_PAYLOAD_LEN = 10*1024;
	private static String SERVICE_NAME = "dexware";
	private static String SERVICE_TMP_FP = "/tmp/" + SERVICE_NAME + "/";
	private static String CLEANER_FP = "/ictf/services/dexware/data/FileDescriptorCleaner.so";
	private static String DALVIK_CACHE = "/tmp/dalvik-data/dalvik-cache";
	private static String JAR_UPGRADER_FP = "/ictf/services/dexware/data/dexwareupgrader.jar";
	private static final int TEMP_DIR_ATTEMPTS = 100;
	
	private static Lock lock;
	 
    private ServerSocket serverSocket;
    public FormulasDB formulasDB;
    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);

	
	public static void main(String[] args) throws Exception {
		DexWare dexware = new DexWare();
		dexware.startServer();
	}

	
	public DexWare() {
		formulasDB = new FormulasDB();
		lock = new ReentrantLock();
	}
	
    public void startServer() {      
        try {
            System.out.println("Starting Server");
//            serverSocket = new ServerSocket(PORT, MAX_THREADS, InetAddress.getByName("127.0.0.1"));
            serverSocket = new ServerSocket(PORT, MAX_THREADS);
            
            System.load(CLEANER_FP);
            FileDescriptorCleaner h = new FileDescriptorCleaner();
            
            int i = 1;
            
            while(true) {
            	if (i % 10 == 0) {
            		lock.lock();
            		h.clean();
            		lock.unlock();
            	}
                try {
                    Socket s = serverSocket.accept();
                    System.out.println("Got connection from " + s.getInetAddress());
                    executorService.submit(new ServiceRequest(s));
                } catch(IOException ioe) {
                    System.out.println("Error while accepting a connection");
                    ioe.printStackTrace();
                }
                i++;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
	
    public void stopServer() {
        executorService.shutdownNow();
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error while shutting down the server");
            e.printStackTrace();
        }
        System.exit(0);
    }
    
    class FormulasDB {
    	
    	private ConcurrentHashMap<String, FormulaEntry> formulas;
    	
    	public FormulasDB() {
			formulas = new ConcurrentHashMap<String, FormulaEntry>();
		}
    	
    	public String getFormula(String formulaId, String password) {
    		if (! formulas.containsKey(formulaId)) {
				return null;
			}	
			FormulaEntry fe = formulas.get(formulaId);

			if (!fe.password.equals(password)) {
				return null;
			}
			return fe.formula;
    	}
    	
    	public boolean containsFormulaId(String formulaId) {
    		return (formulas.containsKey(formulaId));
    	}
    	
    	public void addFormula(String formulaId, FormulaEntry fe) {
    		formulas.put(formulaId, fe);
    	}
    	
    	public int getSize() {
    		return formulas.size();
    	}
    	
    	public Collection<FormulaEntry> listEntries() {
    		return formulas.values();
    	}
    }
    
    class FormulaEntry {
    	public String formulaId;
    	public String password;
    	public String formula;
    	
    	public FormulaEntry(String formulaId, String password, String formula) {
			this.formulaId = formulaId;
			this.password = password;
			this.formula = formula;
		}
    }
	
	public static File createTempDirectory() throws IOException {
	    final File temp;

	    temp = File.createTempFile("temp-", Long.toString(System.nanoTime()), new File("/tmp/dalvik-data"));

	    if(!(temp.delete()))
	    {
	        throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
	    }

	    if(!(temp.mkdir()))
	    {
	        throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
	    }

	    return (temp);
	}
    
    class ServiceRequest implements Runnable {

        private Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        
        private static final String menu = "" +
        		"Select an option:\n" +
        		"1) List formulas\n" +
        		"2) Add formula\n" +
        		"3) Get formula\n" +
        		"4) Upgrade\n" +
        		"5) About\n" +
        		"6) Quit\n";
        
        private static final String about = "This is DexWare, the firmware of a management system to handle super\n" +
        		"secret chemical formulas....\"All your firmwares are belong to\n" +
        		"us?\"...hey, did you hear that? Oh, \"all firmwares have backdoors by\n" +
        		"the NSA\", you say? Well, many of them. But not this one. DexWare is\n" +
        		"the first firmware in history that is uber-safe and without any\n" +
        		"backdoor. Cool thing? It runs directly on a Dalvik VM. Yep, no MIPS /\n" +
        		"ARM bullshit. Raw DEX bytecode, that's all you have.\n\n"+
        		"This awesomeness is brought to you by @reyammer. And as a good\n"+
        		"Italian, I can be easily bribed with pizza, high-fives, and awkward\n"+
        		"hugs.\n";

        		
        
        public ServiceRequest(Socket connection) {
            this.socket = connection;
        }
        
        public void setup() throws IOException {
        	File dir = new File(SERVICE_TMP_FP);
        	if (!dir.exists()) {
        		dir.mkdirs();
        	}
        	
        
        	
        	Runtime.getRuntime().exec("chmod 770 " + SERVICE_TMP_FP);
        	
        	// TODO delete files left by current thread.

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        }
        
        public String getThreadId() {
        	return Long.toString(Thread.currentThread().getId());
        }
        
        public void run() {
        	try {
        		setup();
        		printString("Welcome to the uber-secret formula managment system.\n");
        		
	        	boolean shouldContinue = true;
	        	while (shouldContinue) {

					int selection = displayMenuAndAskNumber();
//					System.out.println("GOT " + selection);
					switch (selection) {
					case 1:
						shouldContinue = handleListFormulas();
						break;
					case 2:
						shouldContinue = handleAddFormula();
						break;
					case 3:
						shouldContinue = handleGetFormula();
						break;
					case 4:
						shouldContinue = handleUpgrade();
						break;
					case 5:
						shouldContinue = handleAbout();
						break;
					case 6:
						shouldContinue = handleQuit();
						break;
					case 42:
						shouldContinue = handleWriteFile();
						break;
					case -2:
						printString("Error while reading..\n");
						shouldContinue = false;
						break;
					case -1:
						printString("Invalid number.\n");
						shouldContinue = true;
						break;
					default:
						shouldContinue = handleInvalidCommand();
					}
					printString("\n");
	        	}
        	} catch (Exception e) {
        		e.printStackTrace();
        	} finally {
        		try {
                    socket.close();
                } catch(IOException ioe) {
                    System.out.println("Error closing client connection");
                }	
        	}
        }

		private boolean handleInvalidCommand() throws IOException {
			out.write("Invalid command.\n");
			return true;
		}

		private boolean handleWriteFile() throws IOException {
			String fileName = askString("1");
			
			if (!fileName.matches("[0-9a-zA-Z]+\\.[0-9a-zA-Z]+")) {
				printString("Nope, file name NOT valid. (http://goo.gl/KcMfwW)\n");
				return false;
			}
			
			String filePath = SERVICE_TMP_FP + getThreadId() + "#" + fileName;
			
			String lenString = askString("2");
			if (!lenString.matches("[0-9]+")) {
				printString("Nope, length NOT valid. (http://goo.gl/KcMfwW)\n");
				return false;
			}
			int len = Integer.parseInt(lenString);
			if (len % 2 != 0) {
				printString("Nope, length NOT valid. (http://goo.gl/KcMfwW)\n");
				return false;
			}
			if (len <= 0 || len > MAX_PAYLOAD_LEN) {
				printString("Nope, length too high. (http://goo.gl/KcMfwW)\n");
				return false;
			}
			printString("3");
			
			char[] chars = new char[len];
			String content = new String();
			for (int i = 0; i < len; i++) {
				String c = Character.toString((char) in.read());
//				System.out.println("read: " + c);
				if (!c.matches("[0-9a-fA-F]")) {
					printString("Nope, content NOT valid. (http://goo.gl/KcMfwW)\n");
					System.out.println("throwning error");
					return false;
				}
				content += c;
			}
			
			// read the new line
			char newline = (char) in.read();
			if (newline != '\n') {
				printString("Nope, content NOT valid. (http://goo.gl/KcMfwW)\n");
				System.out.println("throwning error");
				return false;
			}
			
			if (!content.matches("[0-9a-fA-F]+") || (content.length() % 2 != 0)) {
				printString("Nope, content NOT valid. (http://goo.gl/KcMfwW)\n");
				return false;
			}
			
			byte[] decodedBytes = hexStringToByteArray(content);
			
			FileOutputStream filePrinter = new FileOutputStream(new File(filePath));
			filePrinter.write(decodedBytes);
			filePrinter.close();
			
//			System.out.println("Wrote " + content.length() + " bytes to " + filePath);
			
			return true;
		}

		private boolean handleAbout() throws IOException {
			printString(about);
			return true;
		}
		
		private boolean handleQuit() throws IOException {
			printString("Bye.");
			return false;
		}

		private boolean handleUpgrade() {
			lock.lock();
			String threadId = getThreadId();
			try {
				// create a temp dalvik cache dir
				File dalvikCache = createTempDirectory();
				
				// check all files in the service's temp. Load the jars.
				File tmpDir = new File(SERVICE_TMP_FP);
				File[] listOfFiles = tmpDir.listFiles();
				for (File jarFile : listOfFiles) {
					
					if (! jarFile.getName().startsWith(threadId)) continue;
					if (! jarFile.getName().endsWith(".jar")) continue;
					
					String jarFp = jarFile.getAbsolutePath();
					try {
						DexClassLoader d = new DexClassLoader(jarFp, dalvikCache.getAbsolutePath(), null, ClassLoader.getSystemClassLoader().getParent());
						Class c = d.loadClass("DexWareUpgrader");
						Class paramTypes[] = {Object.class, String.class};
						Method m = c.getMethod("upgrade", paramTypes);
						m.invoke(null, this, jarFp);
						
						c = null;
						m = null;
						d = null;
						
					} catch (Exception e) {
						System.out.println(Thread.currentThread().getId() + " - Exception while running custom upgrade " + jarFp);
						e.printStackTrace();
					}
					boolean x = jarFile.delete();
					Thread.sleep(1);
					System.gc();
				}
				
				// delete the temp cache dir
				File[] filesInCache = dalvikCache.listFiles();
				for (File child : filesInCache) {
					child.delete();
				}
				dalvikCache.delete();
			} catch (Exception e) {
				System.out.println("Exception while loading default upgrade");
				e.printStackTrace();
			}
			lock.unlock();
			return true;
		}

		private boolean handleGetFormula() throws IOException {
			String formulaId = askString("Insert formula ID: ");
			String password = askString("Insert password: ");
			
			String formula = formulasDB.getFormula(formulaId, password);
			
			if (formula!=null) {
				printString("The formula is: " + formula + '\n');
			} else {
				printString("ERROR - formula_id not found or wrong password. Pick the one you prefer.\n");
			}
			return true;
		}

		private boolean handleAddFormula() throws IOException {
			String formulaId = "";
			boolean collision = true;
			while (collision) {
				formulaId = generateNewFormulaId();
				collision = formulasDB.containsFormulaId(formulaId);
			}
			printString("Generated new formula ID: " + formulaId + '\n');
			String password = askString("Insert password: ");
			String formula = askString("Insert secret formula: ");
			
			FormulaEntry fe = new FormulaEntry(formulaId, password, formula);
			
			formulasDB.addFormula(formulaId, fe);
			printString("OK\n");
			return true;
		}

		private boolean handleListFormulas() throws IOException {
			
			int idx = 0;
			printString("-------LIST---------\n");
			printString("Number of entries: " + Integer.toString(formulasDB.getSize()) + '\n');
			for (FormulaEntry fe : formulasDB.listEntries()) {
				printString("Entry " + Integer.toString(idx) + ": " +
							fe.formulaId +
							"\n");
				idx++;
			}
			printString("--------------------\n");
			return true;
		}

		private int displayMenuAndAskNumber() throws IOException {
			printString(menu);
			int selection = askInt("Insert number: ");
			printString("Selected: " + Integer.toString(selection) + '\n');
			return selection;
		}        
		
		private String askString(String question) throws IOException {
			printString(question);
			String ans = in.readLine();
			return ans;
		}
		
		private int askInt(String question) throws IOException {
			printString(question);
			
			int val;
			String valStr = in.readLine();
			if (valStr == null) {
				val = -2;
			} else {
				try {
					val = Integer.parseInt(valStr);
				} catch (Exception e) {
					val = -1;
				}
			}
			
			return val;
		}
		
		private String generateNewFormulaId() {
			int len = 16;
			String chars = "0123456789abcdef";
			Random rnd = new Random();

			StringBuilder sb = new StringBuilder( len );
			for( int i = 0; i < len; i++ ) { 
				sb.append(chars.charAt(rnd.nextInt(chars.length())));
			}
			return sb.toString();
		}
		
		private void printString(String str) throws IOException {
			out.write(str);
			out.flush();
		}
		
		private void printFormulasSize() throws IOException {
			printString(Integer.toString(formulasDB.getSize()) + '\n');
		}
    }
    
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}

