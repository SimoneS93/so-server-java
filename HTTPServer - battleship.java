package httpserver;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Implementazione del server HTTP della traccia sulla battaglia navale
 * @author imac
 */
public class HTTPServer {
    ServerSocket server;
    PrintWriter out;
    BufferedReader in;
    String requestHead;
    String requestBody;
    String responseHead;
    String responseBody;
    Map<String, String> requestHeaders;
    Map<String, String> responseHeaders;
    Set<String> done;

    /**
     * Main
     * @param args
     */
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(12345);
        
        while (true) {
            Socket client = server.accept();
            new HTTPServer(server, client);
        }
    }
    
    /**
     * Costruttore, inizializza gli streams
     * @param client
     * @throws IOException 
     */
    public HTTPServer(ServerSocket server, Socket client) throws IOException {
        this.server = server;
        out = new PrintWriter(client.getOutputStream(), true);
        in  = new BufferedReader(new InputStreamReader(client.getInputStream()));
        requestHead  = null;
        responseHead = "";
        requestBody  = null;
        responseBody = "";
        requestHeaders  = new HashMap<String, String>();
        responseHeaders = new HashMap<String, String>();
        //done dovrebbe essere persistente tra le richieste, salvandolo magari
        //in un file o qualcosa del genere collegato all'utente
        //ora invece viene creato ad ogni richiesta, quindi sar√† vuoto ad ogni mossa
        done = new LinkedHashSet<String>();
        
        responseHeaders.put("Date", new Date().toString());
        log("new http server running on port 12345");
        serve();
    }
    
    /**
     * Interagisci col client
     * Il protocollo echo ritorna al client la stringa ricevuta
     * [Pu√≤ essere chiamato nel metodo run() di un thread]
     */
    private void serve() throws IOException {
        String input;
        
        try {
            while ((input = in.readLine()) != null) {
                //il primo rigo √® l'intestazione della richiesta
                if (requestHead == null) {
                    setRequestHead(input.trim());
                }
                //se body √® null, siamo nella sezione degli header
                else if (requestBody == null) {
                    //ricevuto \r\n che segna l'inizio del body
                    if (input.equals("\\r\\n"))
                        requestBody = "";
                    else
                        readHeader(input.trim());
                }
                //stiamo ricevendo il body -> accetta una solo rigo poi esci
                else {
                    setRequestBody(input.trim());
                    writeResponse();
                    break;
                }
            }
        }
        catch (Exception ex) {
            //setRequest e readHeader lanciano un'eccezione quando si √® verificato un errore
            //poich√® abort scrive gi√† la risposta, qui non bisogna fare niente
        }
        
        server.close();
    }
    
    /**
     * Imposta intestazione richiesta
     * @param input 
     */
    private void setRequestHead(String input) throws Exception {
        String segments[] = input.split(" ");
        
        if (segments.length != 3)
            abort(400, "Bad request", "Request format must be [method resource protocol]");
        else if (!segments[0].equals("POST"))
            abort(501, "Not implemented", "Only POST is supported");
        else if (!segments[1].equals("/fire"))
            abort(404, "Not found", "/fire is the only endpoint I know");
        else if (!segments[2].equals("HTTP/1.0"))
            abort(400, "Bad request", "HTTP/1.0 is the only protocol I know");
        //tutto ok
        else
            requestHead = input;
    }
    
    //input deve essere del tipo [x=N&y=M]
    private void setRequestBody(String input) throws Exception {
        //usa una espressione regolare per validare l'input
        if (!input.matches("^x=([1-9]|10)&y=[A-J]$"))
            abort(400, "Bad request", "Request body must match regex /^x=([1-9]|10)&y=[A-J]$/");
        
        int x = 0;
        String pair[] = input.replace("x=", "").replace("y=", "").split("&");
        String y = pair[1];
        String point = null;
        
        try {
             x = Integer.parseInt(pair[0]);
        }
        catch (NumberFormatException ex) {
            abort(400, "Bad request", "x must be integer");
        }
        
        //qui siamo sicuri che x e y siano corretti perch√® soddisfano l'espressione regolare
        //nel caso non conosceste le espressioni regolari, usiamo classici controlli
        if (x < 0 || x > 10)
            abort(400, "Bad request", "x must be in range [1, 10]");
        if (y.length() > 1 || "ABCDEFGHIJ".indexOf(y) < 0)
            abort(400, "Bad request", "y must be in range [A, J]");
        
        //controlla se la mossa √® gi√† stata fatta
        point = y + x;
        if (done.contains(point))
            abort(400, "Bad request", "You've already done this move");
        else
            done.add(point);
        
        //controlla se si √® colpito il bersaglio
        responseHead = "200 OK";
        if (BattleshipGame.got(x, y))
            responseBody = "You got the enemy";
        else
            responseBody = "Retry :(";
    }
    
    /**
     * Imposta valore header
     * @param input 
     */
    private void readHeader(String input) throws Exception {
        String pair[] = input.split(":", 2);
        
        if (pair.length != 2) {
            abort(400, "Bad request", "Invalid header format");
        }
    }
    
    /**
     * Invia headers al client
     */
    private void writeHeaders() {
        for (String header : responseHeaders.keySet()) {
            out.print(header + ": " + responseHeaders.get(header) + "\r\n");
        }
        out.print("\r\n");
    }
    
    /**
     * Invia risposta al client
     */
    private void writeResponse() {
        out.println("HTTP/1.0 " + responseHead);
        responseHeaders.put("Content-Length", "" + (requestBody != null ? responseBody.length() : 0));
        writeHeaders();
        if (responseBody != null)
            out.println(responseBody);
    }
    
    /**
     * Segnala errore al client
     * @param status
     * @param message
     * @param body
     * @throws Exception 
     */
    private void abort(int status, String message, String body) throws Exception {
        responseHead = status + " " + message;
        responseBody = body;
        writeResponse();
        throw new Exception();
    }
    
    /**
     * Metodo di utilit√† per verificare il flusso del programma
     * @param message 
     */
    private void log(String message) {
        System.out.println("[log] " + message);
    }
    
    
    
    /**
     * Mock della classe BattleshipGame
     * ritorna true se x √® pari
     */
    static class BattleshipGame {
        public static boolean got(int x, String y) {
            return x % 2 == 0;
        }
    }
}
