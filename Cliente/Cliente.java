import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Cliente {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java Cliente <host> <puerto>");
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(args[0]);
            int puertoInicial = Integer.parseInt(args[1]);
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(10000); // Establece un tiempo de espera de 10 segundos

            enviarMensaje(socket, address, puertoInicial, "conectar");

            // Recibe el nuevo puerto para la comunicación dedicada
            byte[] bufferRespuesta = new byte[500];
            DatagramPacket paqueteRespuesta = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);
            socket.receive(paqueteRespuesta);
            String respuesta = new String(paqueteRespuesta.getData(), 0, paqueteRespuesta.getLength());
            System.out.println(respuesta); // Imprime la respuesta del servidor

            String[] partesRespuesta = respuesta.split(" ");
            if (!partesRespuesta[0].equals("puerto")) {
                System.out.println("error al conectarse con el servidor");
                return;
            }
            int puerto = Integer.parseInt(partesRespuesta[1]); // Nuevo puerto asignado por el servidor

            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String comando;
            System.out.println("Ingrese comandos ('login usuario contraseña', 'get <archivo>', 'put <archivo>', 'list', 'delete <archivo>'):");

            while ((comando = userInput.readLine()) != null && !comando.equals("salir")) {
                if (comando.startsWith("put ")) {
                    manejarPut(socket, address, puerto, comando);
                } else {
                    enviarMensaje(socket, address, puerto, comando);
                }

                // Espera la respuesta del servidor para todos los comandos
                paqueteRespuesta = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);
                socket.receive(paqueteRespuesta);
                respuesta = new String(paqueteRespuesta.getData(), 0, paqueteRespuesta.getLength());

                if (comando.startsWith("get ")) {
                    String[] partesComando = comando.split(" ", 2);
                    if (partesComando.length > 1) {
                        guardarArchivo(partesComando[1], paqueteRespuesta.getData(), paqueteRespuesta.getLength());
                    }
                } else {
                    System.out.println(respuesta); // Muestra la respuesta para otros comandos
                }
            }
        } catch (IOException e) {
            System.out.println("Error de comunicación: " + e.getMessage());
        }
    }

    private static void enviarMensaje(DatagramSocket socket, InetAddress address, int puerto, String mensaje) throws IOException {
        byte[] bufferEnvio = mensaje.getBytes();
        DatagramPacket paquete = new DatagramPacket(bufferEnvio, bufferEnvio.length, address, puerto);
        socket.send(paquete);
    }

    private static void manejarPut(DatagramSocket socket, InetAddress address, int puerto, String comando) throws IOException {
        String[] partesComando = comando.split(" ", 2);
        if (partesComando.length > 1) {
            String nombreArchivo = partesComando[1];
            byte[] contenidoArchivo = Files.readAllBytes(Paths.get(nombreArchivo));
            enviarMensaje(socket, address, puerto, comando + " " + new String(contenidoArchivo));
        }
    }

    private static void guardarArchivo(String nombreArchivo, byte[] datos, int longitud) {
        try (FileOutputStream fos = new FileOutputStream(nombreArchivo)) {
            fos.write(datos, 0, longitud);
            System.out.println("Archivo guardado: " + nombreArchivo);
        } catch (IOException e) {
            System.out.println("Error al guardar el archivo: " + e.getMessage());
        }
    }
}
