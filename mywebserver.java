/*--------------------------------------------------------

1. Name / Date:
Paul Hester
02/08/2015

2. Java version used, if not the official version for the class:

java version "1.8.0_25"
Java(TM) SE Runtime Environment (build 1.8.0_25-b18)
Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)

3. Precise command-line compilation examples / instructions:

> javac MyWebServer.java

4. Precise examples / instructions to run this program:

> java MyWebServer

5. List of files needed for running the program.

 a. checklist.html
 b. MyWebServer.java 

5. Notes:

Code was developed on a Windows 7 machine with Eclipse IDE.

----------------------------------------------------------*/

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MyWebServer {
	@SuppressWarnings("resource")
	public static void main(String[] args) {

		DumpStartupInformation();

		ServerSocket servsock = null;
		try {
			servsock = new ServerSocket(2540, 100);
		} catch (IOException e) {
			System.out.println("Cannot initialize ServerSocket.");
			System.exit(1);
		}

		Socket socket;

		while (true) {
			System.out.println("Ready for connection...");
			try {
				socket = servsock.accept();
			} catch (IOException e) {
				continue;
			}
			System.out.println("Has connection. Beginning to process...");
			new ClientHandler(socket).start();
		}
	}

	public static void DumpStartupInformation() {
		StringBuilder b = new StringBuilder();
		b.append("---------------------------------------------------------\n");
		b.append("Paul Hester's Web Server Starting\n");
		b.append("Port: 2540\n");
		b.append("Development Environment: Windows 7 and Eclipse IDE\n");
		b.append("---------------------------------------------------------\n");

		System.out.println(b.toString());
		Logger.getInstance().write(b.toString());
	}
}

/**
 * @author Paul Hester
 * @description Handles each connecting client on a new thread.
 */
class ClientHandler extends Thread {
	Socket sock;

	public ClientHandler(Socket socket) {
		this.sock = socket;
	}

	public void run() {

		PrintStream out = null;
		BufferedReader in = null;

		try {
			in = new BufferedReader(
					new InputStreamReader(sock.getInputStream()));
			out = new PrintStream(sock.getOutputStream());

			// Parse first line - See what type of request.	For this assignment, we're not concerned with other headers.		
			HTTPRequest request = HTTPParser.GetRequest(in.readLine());
			HTTPResponse response = HTTPHandler.ProcessRequest(request);

			// Generate Response.
			out.print(HTTPHandler.GetHTTPResponseString(response));
			out.flush();

		} catch (IOException e) {
			System.out.println("Failure trying to handle client");
		} finally {
			try {
				this.sock.close();
			} catch (IOException e) {
				System.out.println("Cannot close client socket.");
			}
		}
	}
}

/**
 * @author Paul Hester
 * @description HTTPRequest used for processing.
 */
class HTTPRequest {
	String Method;
	String Resource;
	String Protocol;
	String Message;
	HashMap<String, String> Headers;
	HashMap<String, String> Parameters;
	boolean Invalid;
}

/**
 * @author Paul Hester
 * @description HTTPResponse used for processing.
 */
class HTTPResponse {
	int StatusCode;
	int ContentLength;
	String MIME;
	String Body;
}

/**
 * @author Paul Hester
 * @description Used to create an HTTPRequest object for use.
 */
class HTTPParser {
	public static HTTPRequest GetRequest(String request) {
		// I.	Split first request line by " " to get three important items. Method, Resource, Protocol
		// II.	If we do not find the three items needed, request is bad.
		// III.	Setup HTTPRequest object with required values.
		// IV.	Check to see if method is supported.

		// I.
		String[] segments = request.split(" ");

		HTTPRequest r = new HTTPRequest();

		// II.
		if (segments.length != 3) {
			r.Invalid = true;
			r.Message = "Method, Protocol Version or Resource is missing.";
			return r;
		}

		// III.
		r.Method = segments[0];
		r.Resource = segments[1];
		r.Protocol = segments[2];
		r.Parameters = HTTPParser.ParseQueryString(r.Resource);

		// VI. 
		if (!HTTPValidator.getInstance().IsMethodSupported(r.Method)) {
			r.Invalid = true;
			r.Message = "Method is not supported.";
		}

		return r;
	}

	public static HashMap<String, String> ParseQueryString(String resource) {
		// I.	? signifies a query string is being passed. If none, return null;
		// II.	Extract query string from resource request.
		// III.	Split query string key value pairs by &.
		// IV.	Fill HashMap with pairs.
		// V.	Return HashMap.

		// I.
		int start = resource.indexOf('?');
		if (start == -1)
			return null;

		// II.
		int end = resource.length();
		String query = resource.substring(start + 1, end);

		// III.
		String[] pairs = query.split("&");

		// IV.		
		HashMap<String, String> kv = new HashMap<String, String>();
		for (int i = 0; i < pairs.length; i++) {
			String[] segments = pairs[i].split("=");
			if (segments.length == 2)
				kv.put(segments[0], segments[1]);
		}

		// V.
		return kv;
	}
}

/**
 * @author Paul Hester
 * @description Fake CGI addnums method.
 */
class FakeCGI {
	public static int AddNumbers(int num1, int num2) {
		return num1 + num2;
	}
}

/**
 * @author Paul Hester
 * @description Fake CGI addnums method.
 */
class HTTPValidator {
	private static HTTPValidator instance;
	private HashSet<String> methods; // O(1) Add and Contains
	private HashSet<String> protocols; // O(1) Add and Contains

	public HTTPValidator() {
		// I.	Setup supported methods.
		// II.	Setup supported protocols.

		// I.
		this.methods = new HashSet<String>();
		this.methods.add("GET");
		this.methods.add("POST");

		// II.
		this.protocols = new HashSet<String>();
		this.protocols.add("HTTP/1.1");
	}

	public static synchronized HTTPValidator getInstance() {
		if (instance == null) {
			instance = new HTTPValidator();
		}
		return instance;
	}

	public boolean IsMethodSupported(String method) {
		if (this.methods.contains(method))
			return true;
		return false;
	}

	public boolean IsProtocolSupported(String protocol) {
		if (this.methods.contains(protocol))
			return true;
		return false;
	}
}

/**
 * @author Paul Hester
 * @description Will process the HTTPRequest and create a response.
 */
class HTTPHandler {

	public static HTTPResponse ProcessRequest(HTTPRequest request) {
		// I.	Dump request information to log.
		// II.	Determine what the request is for. File, Directory listing, Fake CGI.
		// II.	A. 	File is found. Read contents and process.
		// II.	B.	Directory is found. Create directory listing HTML response.
		// II.	C.	Handle Fake CGI request.
		// II.	D.	Still haven't found what you're looking for.
		// III.	Dump response information.
		// IV.	Return response.

		// I.
		Logger.getInstance().write("Beginning to process request");
		Logger.getInstance().write("Request Method", request.Method);
		Logger.getInstance().write("Request Resource", request.Resource);
		System.out.println("Processing request for " + request.Resource);

		HTTPResponse response = new HTTPResponse();

		// II.
		if (FileProvider.IsFileAndExists(request.Resource)) {
			// II A.
			Logger.getInstance().write("Resource is a FILE.");
			HTTPHandler.ProcessFile(request.Resource, response);
		} else if (FileProvider.IsDirectoryAndExists(request.Resource)) {
			// II B.
			Logger.getInstance().write("Resource is a DIRECTORY.");
			HTTPHandler.ProcessDirectory(request.Resource, response);
		} else {
			String ext = FileProvider.GetExtension(request.Resource);
			if (ext != null && ext.equals("fake-cgi")) {
				Logger.getInstance().write("Resource is Fake CGI.");
				HTTPHandler.ProcessCGI(request, response);
			} else {
				// II D.
				Logger.getInstance().write("Resource NOT FOUND.");
				HTTPHandler.Process404(response);
			}
		}

		response.ContentLength = response.Body.length();

		// III.
		Logger.getInstance().write("Response Content Type", response.MIME);
		Logger.getInstance().write("Response Content Length",
				String.valueOf(response.ContentLength));
		Logger.getInstance().write("Response Body",
				String.valueOf(response.Body));

		// IV.
		return response;
	}

	public static void ProcessFile(String resource, HTTPResponse response) {
		// I.	Get file extension to return proper content-type.
		// II.	Read file contents if not cached.
		// III.	Setup MIME based on extension. If MIME lookup is bad, just use plain txt.
		// IV.	Setup status code.

		// I.
		String extension = FileProvider.GetExtension(resource);

		// II. 
		response.Body = FileProvider.ReadFile(resource);

		// III.
		response.MIME = Server.getInstance().GetMIME(extension);
		if (response.MIME == null)
			response.MIME = Server.getInstance().GetMIME("txt");

		// IV.
		response.StatusCode = 200;
	}

	public static void ProcessCGI(HTTPRequest request, HTTPResponse response) {
		// I.	Parse key value pairs from query.
		// II.	Setup variables for processing. TODO: If cannot find any needed values, 403.
		// III. Format body with values for response.
		// IV.	Get MIME for content type return.
		// V.	Setup response content length.

		// I.
		if (request.Parameters != null) {
			// II.
			try {
				String person = request.Parameters.get("person");
				int num1 = Integer.parseInt(request.Parameters.get("num1"));
				int num2 = Integer.parseInt(request.Parameters.get("num2"));
				int total = FakeCGI.AddNumbers(num1, num2);

				// III.
				response.Body = String.format(
						"Dear %s, the sum of %d and %d is %d.", person, num1,
						num2, total);
			} catch (Exception e) {
				response.Body = "<h1>Could not execute addnums function.</h1>";
			}

		} else
			response.Body = "<h1>Query string parameters cannot be mapped.</h1>";

		// IV.
		response.MIME = Server.getInstance().GetMIME(
				FileProvider.GetExtension(request.Resource));

		// V.
		response.ContentLength = response.Body.length();
	}

	public static void Process404(HTTPResponse response) {
		// I. Setup 404.

		// I.
		response.StatusCode = 404;
		response.Body = "<h1>Resource Not Found.</h1>";
		response.MIME = Server.getInstance().GetMIME("html");
		response.ContentLength = response.Body.length();
	}

	public static void ProcessDirectory(String resource, HTTPResponse response) {
		// I.	Directory lists are being built.
		// II.	MIME will be HTML to list contents.
		// III.	Response success

		// I.
		response.Body = HTTPHandler.GetDirectoryListing(resource);

		// II.
		response.MIME = Server.getInstance().GetMIME("html");

		// III.
		response.StatusCode = 200;
	}

	public static String GetHTTPResponseString(HTTPResponse r) {
		// TODO: Implement status message.

		return String
				.format("HTTP/1.1 %d \r\nContent-Length: %d\r\nContent-Type: %s\r\n\r\n%s",
						r.StatusCode, r.ContentLength, r.MIME, r.Body);
	}

	public static String GetDirectoryListing(String directory) {
		// I. 	Get list of directory items.
		// II.	Handle / request. Create link format.
		// III.	Generate HTML to display list. UL is used as element.
		// IV.	Return HTML.

		StringBuilder b = new StringBuilder();

		// I.
		if (directory.contains("../")) // Stop traversal. Show only root.
			directory = "/";
		File[] fd = FileProvider.GetDirectoryContents(directory);

		// II.
		String format = (directory.equals("/")) ? "<li><a href=\"%s%s\">%s</a></li>"
				: "<li><a href=\"%s/%s\">%s</a></li>";

		// III.
		b.append(String.format("<h1>Index of %s</h1>", directory));
		b.append("<ul>");
		for (int i = 0; i < fd.length; i++) {
			String name = fd[i].getName();
			b.append(String.format(format, directory, name, name));
		}
		b.append("</ul>");

		// IV.
		return b.toString();
	}
}

/**
 * @author Paul Hester
 * @description Handle all file processing.
 */
class FileProvider {

	public static boolean IsFileAndExists(String resource) {
		// TODO: Guard again directory traversal
		File f = new File(Server.getInstance().GetRootPath() + resource);
		return (f.exists() && !f.isDirectory()) ? true : false;
	}

	public static boolean IsDirectoryAndExists(String resource) {
		// TODO: Guard againt directory traversal
		File f = new File(Server.getInstance().GetRootPath() + resource);
		return (f.exists() && f.isDirectory()) ? true : false;
	}

	public static String ReadFile(String name) {
		try {
			File item = new File(Server.getInstance().GetRootPath() + name);
			return readFile(item.getCanonicalPath());
		} catch (IOException e) {
			System.out.println("FILE DOESN'T EXIST");
			return null;
		}
	}

	public static String readFile(String path) {
		byte[] encoded;
		try {
			encoded = Files.readAllBytes(Paths.get(path));
			return new String(encoded, "UTF8");
		} catch (IOException e) {
			return null;
		}
	}

	public static String GetExtension(String resource) {
		String ext = null;
		int extension = resource.lastIndexOf('.');
		int query = resource.indexOf('?');

		if (query >= 0 && extension >= 0) {
			ext = resource.substring(extension + 1, query);
		} else if (extension >= 0 && query <= 0) {
			ext = resource.substring(extension + 1, resource.length());
		}

		return ext;
	}

	public static File[] GetDirectoryContents(String resource) {
		File d = new File(Server.getInstance().GetRootPath() + resource);
		return d.listFiles();
	}

	public static File GetParentDirectory(String resource) {
		File d = new File(Server.getInstance().GetRootPath() + resource);
		return d.getParentFile();
	}
}

/**
 * @author Paul Hester
 * @description Server singleton that has information about server supported
 *              items.
 */
class Server {
	private HashMap<String, String> mimeTypes;
	private HashMap<String, String> recentlyRequestedItems;
	private static Server instance;
	public String root;

	public Server() {
		// I.	Create map of supported MIME types.
		// II.	Create cache for files requested that haven't changed. Not fully implemented.
		// III.	Setup root path.

		// I.
		this.mimeTypes = new HashMap<String, String>();
		this.mimeTypes.put("txt", "text/plain");
		this.mimeTypes.put("html", "text/html");
		this.mimeTypes.put("ico", "image/x-icon");
		this.mimeTypes.put("fake-cgi", "text/html");
		this.mimeTypes.put("java", "text/plain");

		// II.
		this.recentlyRequestedItems = new HashMap<String, String>();

		// III.
		try {
			this.root = new File(".").getCanonicalPath();
		} catch (IOException e) {
			System.out
					.println("Cannot map location to server files from... Process is exiting...");
			System.exit(0);
		}
	}

	public static synchronized Server getInstance() {
		if (instance == null) {
			instance = new Server();
		}
		return instance;
	}

	public String GetMIME(String ext) {
		if (ext != null) {
			return this.mimeTypes.get(ext.toLowerCase());
		}
		return this.mimeTypes.get("txt");
	}

	public String GetCachedItem(String resource) {
		return this.recentlyRequestedItems.get(resource);
	}

	public String PutItemInCache(String resource, String body) {
		return this.recentlyRequestedItems.put(resource, body);
	}

	public String GetRootPath() {
		return this.root;
	}
}

/**
 * @author Paul Hester
 * @description A singleton to be used for logging. Each entry is recorded with
 *              epoch time and the id of current thread.
 */
class Logger {
	private static Logger instance;
	private PrintWriter printWriter;
	private FileWriter fileWriter;
	private File file;

	public Logger() {
		// I.	Init objects needed to log to JokeOutput.txt file. If an exception is thrown, 
		//		just display warning and not bring down the entire server

		try {
			// I.
			file = new File("serverlog.txt");
			fileWriter = new FileWriter(file, true);
			printWriter = new PrintWriter(fileWriter, true);
		} catch (Exception e) {
			System.out
					.println("WARNING... Logging will not working for this session.");
			file = null;
			fileWriter = null;
			printWriter = null;
		}
	}

	public static synchronized Logger getInstance() {
		// I.	Create thread-safe instance of singleton Logger.

		// I.
		if (instance == null) {
			instance = new Logger();
		}
		return instance;
	}

	public synchronized void write(String message) {
		// I.	Write message using standard thread id, epoch time format.

		// I.
		if (this.printWriter != null)
			printWriter.printf("%s %s - %s\r\n", System.currentTimeMillis(),
					Thread.currentThread().getId(), message);
	}

	public synchronized void write(String prefix, String message) {
		// I.	Write message using standard thread id, epoch time format, prefix and then message.

		// I.		
		if (this.printWriter != null)
			printWriter.printf("%s %s - %s %s\r\n", System.currentTimeMillis(),
					Thread.currentThread().getId(), prefix, message);
	}

	public void close() {
		try {
			if (this.fileWriter != null)
				fileWriter.close();
			if (this.printWriter != null) {
				printWriter.flush();
				printWriter.close();
			}
		} catch (Exception e) {
			System.out
					.println("WARNING... Was not able to close and flush PrintWriter.");
		}
	}
}
