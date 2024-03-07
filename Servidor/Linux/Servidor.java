import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Servidor {
    private static final int puertoNuevo = 50000;
    private static int puertoActual = puertoNuevo;

    public static void main(String[] args) throws IOException {
        // vemos si estamos metiendo dos argumentos o no
        if (args.length != 2) {
            System.out.println("java Servidor host puerto");
            return;
        }
        // recogemos el puerto del argumento nº 1, la ip con el nº 0 y configurar el
        // socket del servidor con ambos
        String direccionIP = args[0];
        int puerto = Integer.parseInt(args[1]);
        InetAddress direccion = InetAddress.getByName(direccionIP);
        DatagramSocket socket = new DatagramSocket(puerto, direccion);
        System.out.println("servidor UDP iniciado en puerto " + puerto + " en la direccion: " + direccionIP);

        while (true) {
            // configuramos el buffer y el paquete para recibir datos
            byte[] buffer = new byte[500];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            // convertimos los datos recibidos a cadena de texto
            String mensaje = new String(packet.getData(), 0, packet.getLength());

            // aqui la logica que he pensado es que el cliente mande un mensaje diciendo conectar de manera
            // automatica para notificar al servidor
            // de que se ha conectado un cliente nuevo y que hay que pasarle el puerto el
            // cual se usara para la nueva conexion al hilo que se ejecutará
            // para dicho cliente.
            if (mensaje.equals("conectar")) {
                // incrementamos el puerto actual

                puertoActual++;

                // creamos un nuevo hilo para el cliente con el nuevo puerto
                new Thread(new HiloServidor(puertoActual, packet.getAddress(), packet.getPort())).start();

                // y ahora le mandamos el cliente el puerto para dicho hilo
                String msgPuerto = "puerto " + puertoActual;
                byte[] datosPuerto = msgPuerto.getBytes();
                DatagramPacket paquetePuerto = new DatagramPacket(datosPuerto, datosPuerto.length, packet.getAddress(),
                        packet.getPort());
                socket.send(paquetePuerto);
            }
        }
    }
}

class HiloServidor implements Runnable {
    private int puerto;
    private DatagramSocket socketCliente;
    private byte[] buffer = new byte[500];
    private String usuarioActual = "anonymous";

    public HiloServidor(int puerto, InetAddress direccionCliente, int puertoCliente) {
        // configuramos el socket del cliente y crear la carpeta para anonymous
        this.puerto = puerto;
        try {
            this.socketCliente = new DatagramSocket(this.puerto);
            new File("anonymous").mkdirs();
        } catch (SocketException e) {
            System.out.println("no se pudo abrir el socket en el puerto " + this.puerto);
            return;
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // configuramos el datagrama para recibir los datos
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socketCliente.receive(packet);

                // convertimos los datos recibidos a texto e interpretar el comando
                String comando = new String(packet.getData(), 0, packet.getLength());
                String[] tokens = comando.split(" ", 4);
                byte[] responseData = "error".getBytes();

                // segun el comando guardado en el primer lugar de tokens, entraremos a un caso
                // u otro comando
                switch (tokens[0]) {
                    case "login":
                        // logueamos al usuario y enviamos mensaje del ok o error
                        if (tokens.length > 2 && autenticarUsuario(tokens[1], tokens[2])) {
                            usuarioActual = tokens[1];
                            new File(usuarioActual).mkdirs();
                            responseData = ("login correcto. bienvenido, " + usuarioActual).getBytes();
                        } else {
                            responseData = "error de login".getBytes();
                        }
                        break;
                    case "get":
                        // en este caso mandamos archivo de la carpeta del usuario logueado y mandamos
                        // al cliente
                        if (!"anonymous".equals(usuarioActual)
                                || "anonymous".equals(usuarioActual) && tokens[0].equals("get")) {
                            Path path = Paths.get(usuarioActual + "/" + tokens[1]);
                            if (Files.exists(path)) {
                                responseData = Files.readAllBytes(path);
                            } else {
                                responseData = "Archivo no encontrado".getBytes();
                            }
                        }
                        break;
                    case "put":
                        // en este caso el cliente nos manda un archivo y se guarda en el servidor, en
                        // la carpeta del usuario logueado
                        if (!"anonymous".equals(usuarioActual)) {
                            if (tokens.length > 2) {
                                Path path = Paths.get(usuarioActual + "/" + tokens[1]);
                                byte[] data = Arrays.copyOfRange(packet.getData(),
                                        tokens[0].length() + tokens[1].length() + 2, packet.getLength());
                                Files.write(path, data);
                                responseData = "Archivo almacenado".getBytes();
                            }
                        } else {
                            responseData = "Operación no permitida para anonymous".getBytes();
                        }
                        break;
                        case "list":
                        // vemos los ficheros que tenemos en nuestro directorio mediante el comando dir
                        if (!"anonymous".equals(usuarioActual) || "anonymous".equals(usuarioActual)) {
                            try {
                                // ejecutamos el comando ls -l en el directorio del usuario
                                ProcessBuilder processBuilder = new ProcessBuilder("ls", "-l", usuarioActual);
                                Process process = processBuilder.start();
                    
                                // capturar la salida del proceso
                                InputStream inputStream = process.getInputStream();
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                byte[] buffer = new byte[500];
                                int bytesRead;
                    
                                // mostramos la salida del proceso
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                    
                                // hay que esperar a que el proceso termine
                                int exitCode = process.waitFor();
                    
                                // enviamos los datos
                                if (exitCode == 0) {
                                    responseData = outputStream.toByteArray();
                                } else {
                                    responseData = ("Error ejecutando 'ls -l': " + exitCode).getBytes();
                                }
                    
                                // cerramos flujos
                                outputStream.close();
                                inputStream.close();
                            } catch (InterruptedException e) {
                                responseData = ("Error ejecutando 'ls -l': " + e.getMessage()).getBytes();
                            }
                        }
                        break;
                    case "delete":
                        // eliminar el archivo que se ha llamado en el directorio del usuario logueado
                        if (!"anonymous".equals(usuarioActual)) {
                            if (tokens.length > 1) {
                                Files.deleteIfExists(Paths.get(usuarioActual + "/" + tokens[1]));
                                responseData = "archivo eliminado".getBytes();
                            }
                        } else {
                            responseData = "operacion no permitida para anonymous".getBytes();
                        }
                        break;
                }

                // mandamos la respuesta al cliente despues de ejecutar la logica del comando
                // ejecutado y guardado en reponseData
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socketCliente.send(responsePacket);
            } catch (IOException e) {
                System.out.println("error manejando cliente: " + e.getMessage());
                break;
            }
        }
    }

    // lo usamos para loguear un usuario con el archivo encontrado en la misma
    // ubicacion que el servidor
    private boolean autenticarUsuario(String usuario, String pass) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("cuentas.txt"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] partes = line.split(":");
                if (partes.length == 2 && partes[0].equals(usuario) && partes[1].equals(pass)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (IOException e) {
            System.out.println("error al autenticar usuario: " + e.getMessage());
        }
        return false;
    }
}
