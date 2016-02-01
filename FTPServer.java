package ftpserver;
import java.io.*;
import java.net.*;


/**
 * Implementa i principali comandi di un server FTP
 * Elenco comandi implementati:
 *  - USER username
 *  - PASS password
 *  - CD folder
 *  - LIST
 *  - RETR filename
 *  - QUIT
 * @author imac
 */
public class FTPServer {
    /**
     * Segnala che lo username è corretto
     */
    final static int STATE_USER_OK = 1;
    /**
     * Segnala che la password è corretta
     */
    final static int STATE_PASS_OK = 2;
    /**
     * Server socket
     */
    ServerSocket server;
    /**
     * Client socket
     */
    Socket client;
    /**
     * Output stream del client
     */
    PrintWriter out;
    /**
     * Input stream del client
     */
    BufferedReader in;
    /**
     * Root del filesystem del server
     */
    String root;
    /**
     * Cartella corrente (modificabile con cd)
     */
    String cwd;
    /**
     * Stato della connessione
     */
    int state;

    /**
     * Main
     * @param args
     */
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(12345);
        
        while (true) {
            //istanzia un nuovo server ad ogni connessione
            Socket client = server.accept();
            FTPServer ftp = new FTPServer(server, client);
            ftp.serve();
        }
    }
    
    /**
     * Costruttore, inizializzazione
     * @param client
     * @throws IOException 
     */
    public FTPServer(ServerSocket server, Socket client) throws IOException {
        this.server = server;
        this.client = client;
        out  = new PrintWriter(client.getOutputStream(), true);
        in   = new BufferedReader(new InputStreamReader(client.getInputStream()));
        root = "/Users/imac/";
        cwd  = "";
        log("new ftp server running on port 12345");
    }
    
    /**
     * Interagisci col client
     * Il protocollo echo ritorna al client la stringa ricevuta
     * [Può essere chiamato nel metodo run() di un thread]
     */
    private void serve() throws IOException {
        String request,
                command,
                username = "";
        
        out.println("530 Please login with username and password");
        
        //leggi continuamente input dal client e smista alla funzione opportuna
        while ((request = in.readLine()) != null) {
            command = request.substring(0, 4);
            log("request: " + request);
            
            
            if (command.equals("USER")) {
                username = request.substring(5);
                testUserExists(username);
            }
            
            else if (command.equals("PASS")) {
                testUserPassword(username, request.substring(5));
            }
            
            else if (command.equals("QUIT")) {
                quit();
                break;
            }
            
            else if (command.substring(0, 3).equals("CD ")) {
                if (requireAuth())
                    cd(request.substring(3));
            }

            else if (command.equals("LIST")) {
                if (requireAuth())
                    ls();
            }

            else if (command.equals("RETR")) {
                if (requireAuth())
                    sendFile(request.substring(5));
            }
            
            else
                out.println("400 Bad request");
        }
        
        server.close();
    }
    
    /**
     * Controlla se l'utente esiste
     * @param username 
     */
    private boolean testUserExists(String username) {
        //accesso anonimo
        if (username.equals("anonymous")) {
            updateState(STATE_USER_OK, true);
            out.println("331 Please specify the password");
            return true;
        }
        else {
            updateState(STATE_USER_OK, false);
            out.println("431 Wrong username");
            return false;
        }
    }
    
    /**
     * Controlla corrispondenza utente-password
     * @param username
     * @param password 
     */
    private boolean testUserPassword(String username, String password) {
        //assicurati che sia stato già specificato lo username
        if (hasState(STATE_USER_OK)) {
            //accesso anonimo, qualsiasi password va bene
            updateState(STATE_PASS_OK, true);
            out.println("230 Login successful");
            return true;
        }
        else {
            updateState(STATE_PASS_OK, false);
            out.println("332 Please specify a username first");
            return false;
        }
    }
    
    /**
     * Cambia directory
     * @param folder 
     */
    private boolean cd(String folder) {
        cwd = folder;
        out.println("200 Now you're in " + root + cwd);
        return true;
    }
    
    /**
     * Elenca file nella cartella corrente
     */
    private boolean ls() {
        File folder = new File(root + cwd);
        
        if (folder.exists() && folder.isDirectory()) {
            out.println("150 Here comes the directory listing.");
            for (File f : folder.listFiles())
                out.println(f.getName());
            out.println("226 Directory send OK.");
            return true;
        }
        else {
            out.println("404 File not found or not a directory.");
            return false;
        }
    }
    
    /**
     * Invia file al client
     * @param filename 
     */
    private boolean sendFile(String filename) {
        filename = root + cwd + filename;
        File file = new File(filename);

        if (file.exists()) {
            try {
                //leggi contenuto binario in un buffer e scrivi sul socket del client
                int length = (int) file.length();
                byte buffer[] = new byte[length];
                BufferedInputStream fileReader = new BufferedInputStream(new FileInputStream(file));
                fileReader.read(buffer, 0, length);
                client.getOutputStream().write(buffer);
                client.getOutputStream().flush();
                return true;
            }
            catch (Exception ex) {
                out.println("500 internal server error");
                return false;
            }
        }
        else {
            out.println("404 File not found");
            return false;
        }
    }
    
    /**
     * Chiudi sessione col client
     */
    private boolean quit() {
        updateState(STATE_USER_OK, false);
        updateState(STATE_PASS_OK, false);
        out.println("221 Goodbye.");
        return true;
    }
    
    /**
     * Assicurati che l'utente sia autenticato prima di eseguire un comando
     */
    private boolean requireAuth() {
        if (!hasState(STATE_USER_OK)) {
            out.println("332 Please specify a username");
            return false;
        }
        else if (!hasState(STATE_PASS_OK)) {
            out.println("331 Please specify the password");
            return false;
        }
        else {
            return true;
        }
    }
    
    /**
     * Aggiorna stato della sessione
     * @param newState
     * @param value 
     */
    private void updateState(int newState, boolean value) {
        //usa la logica bitwise -> metti in OR lo stato corrente e il nuovo
        //se value è false, nega il nuovo stato
        state |= value ? newState : ~newState;
        log("new state: " + state);
    }
    
    /**
     * Ottieni se la sessione soddisfa un certo stato
     * @param state
     * @return 
     */
    private boolean hasState(int state) {
        return (this.state & state) > 0;
    }
    
    /**
     * Metodo di utilità per verificare il flusso del programma
     * @param message 
     */
    private void log(String message) {
        System.out.println("[log] " + message);
    }
}
