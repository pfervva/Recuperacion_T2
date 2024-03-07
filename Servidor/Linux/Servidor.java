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
    private static final int PUERTO_BASE = 50000;
    private static int puertoActual = PUERTO_BASE;

    public static void main(String[] args) throws IOException {
        // Verificar si se proporcionan la dirección del host y el puerto como argumentos
        if (args.length != 2) {
            System.out.println("Uso: java Servidor <host> <puerto>");
            return;
        }

        // Obtener el puerto desde los argumentos y configurar el socket del servidor
        int puerto = Integer.parseInt(args[1]);
        DatagramSocket socket = new DatagramSocket(puerto);
        System.out.println("Servidor UDP corriendo en puerto " + puerto);

        while (true) {
            // Configurar el buffer y el paquete para recibir datos
            byte[] buffer = new byte[500];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            // Convertir los datos recibidos a una cadena de texto
            String mensaje = new String(packet.getData(), 0, packet.getLength());

            // Verificar si el mensaje recibido es "conectar"
            if (mensaje.equals("conectar")) {
                // Incrementar el puerto actual de manera sincronizada
                synchronized (Servidor.class) {
                    puertoActual++;
                }
                // Obtener el nuevo puerto y crear un nuevo hilo para manejar al cliente
                int nuevoPuerto = puertoActual;
                new Thread(new NuevoManejadorDeCliente(nuevoPuerto, packet.getAddress(), packet.getPort())).start();

                // Enviar al cliente el nuevo puerto asignado
                String msgPuerto = "nuevoPuerto " + nuevoPuerto;
                byte[] datosPuerto = msgPuerto.getBytes();
                DatagramPacket paquetePuerto = new DatagramPacket(datosPuerto, datosPuerto.length, packet.getAddress(), packet.getPort());
                socket.send(paquetePuerto);
            }
        }
    }
}

class NuevoManejadorDeCliente implements Runnable {
    private int puerto;
    private DatagramSocket socketCliente;
    private byte[] buffer = new byte[65507];
    private String usuarioActual = "anonymous";

    public NuevoManejadorDeCliente(int puerto, InetAddress direccionCliente, int puertoCliente) {
        // Configurar el nuevo socket del cliente y crear un directorio para el usuario
        this.puerto = puerto;
        try {
            this.socketCliente = new DatagramSocket(this.puerto);
            new File("anonymous").mkdirs();
        } catch (SocketException e) {
            System.out.println("No se pudo abrir el socket en el puerto " + this.puerto);
            return;
        }
    }

    @Override
    public void run() {
        // Verificar si el socket del cliente es válido
        if (this.socketCliente == null) {
            return;
        }

        while (true) {
            try {
                // Configurar el paquete para recibir datos del cliente
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socketCliente.receive(packet);

                // Convertir los datos recibidos a una cadena de texto y procesar el comando
                String comando = new String(packet.getData(), 0, packet.getLength());
                String[] tokens = comando.split(" ", 4);
                byte[] responseData = "error".getBytes();

                // Procesar diferentes comandos del cliente
                switch (tokens[0]) {
                    case "login":
                        // Autenticar al usuario y enviar mensaje de éxito o error
                        if (tokens.length > 2 && autenticarUsuario(tokens[1], tokens[2])) {
                            usuarioActual = tokens[1];
                            new File(usuarioActual).mkdirs();
                            responseData = ("login exitoso. Bienvenido, " + usuarioActual).getBytes();
                        } else {
                            responseData = "error de autenticacion".getBytes();
                        }
                        break;
                    case "get":
                        // Obtener un archivo del usuario y enviar su contenido
                        if (!"anonymous".equals(usuarioActual) || "anonymous".equals(usuarioActual) && tokens[0].equals("get")) {
                            Path path = Paths.get(usuarioActual + "/" + tokens[1]);
                            if (Files.exists(path)) {
                                responseData = Files.readAllBytes(path);
                            } else {
                                responseData = "Archivo no encontrado".getBytes();
                            }
                        }
                        break;
                    case "put":
                        // Almacenar un archivo enviado por el cliente
                        if (!"anonymous".equals(usuarioActual)) {
                            if (tokens.length > 2) {
                                Path path = Paths.get(usuarioActual + "/" + tokens[1]);
                                byte[] data = Arrays.copyOfRange(packet.getData(), tokens[0].length() + tokens[1].length() + 2, packet.getLength());
                                Files.write(path, data);
                                responseData = "Archivo almacenado".getBytes();
                            }
                        } else {
                            responseData = "Operación no permitida para anonymous".getBytes();
                        }
                        break;
                        case "list":
                        // Obtener la lista de archivos del usuario y enviarla
                        if (!"anonymous".equals(usuarioActual) || "anonymous".equals(usuarioActual)) {
                            try {
                                // Ejecutar el comando ls -l en el directorio del usuario
                                ProcessBuilder processBuilder = new ProcessBuilder("ls", "-l", usuarioActual);
                                Process process = processBuilder.start();
                    
                                // Capturar la salida del proceso
                                InputStream inputStream = process.getInputStream();
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                    
                                // Leer la salida del proceso
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                    
                                // Esperar a que el proceso termine
                                int exitCode = process.waitFor();
                    
                                // Enviar los resultados al cliente
                                if (exitCode == 0) {
                                    responseData = outputStream.toByteArray();
                                } else {
                                    responseData = ("Error ejecutando 'ls -l': " + exitCode).getBytes();
                                }
                    
                                // Cerrar los flujos
                                outputStream.close();
                                inputStream.close();
                            } catch (IOException | InterruptedException e) {
                                responseData = ("Error ejecutando 'ls -l': " + e.getMessage()).getBytes();
                            }
                        }
                        break;
                    case "delete":
                        // Eliminar un archivo del usuario
                        if (!"anonymous".equals(usuarioActual)) {
                            if (tokens.length > 1) {
                                Files.deleteIfExists(Paths.get(usuarioActual + "/" + tokens[1]));
                                responseData = "Archivo eliminado".getBytes();
                            }
                        } else {
                            responseData = "Operación no permitida para anonymous".getBytes();
                        }
                        break;
                }

                // Enviar la respuesta al cliente
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socketCliente.send(responsePacket);
            } catch (IOException e) {
                System.out.println("Error manejando cliente: " + e.getMessage());
                break;
            }
        }
    }

    // Método para autenticar un usuario con un archivo de cuentas
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
            System.out.println("Error al autenticar usuario: " + e.getMessage());
        }
        return false;
    }
}
