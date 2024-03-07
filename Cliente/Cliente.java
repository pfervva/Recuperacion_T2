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
            System.out.println("java Cliente host puerto");
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(args[0]);
            int puertoInicial = Integer.parseInt(args[1]);
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(5000); // esperamos 5 segundos 
            // mandamos el mensaje que despierta la conexion del servidor, seguido se pocrede a mandar desde el servidor
            //el puerto al cliente
            enviarMensaje(socket, address, puertoInicial, "conectar");

            // aqui recibimos el puerto
            byte[] bufferRespuesta = new byte[500];
            DatagramPacket paqueteRespuesta = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);
            socket.receive(paqueteRespuesta);
            String respuesta = new String(paqueteRespuesta.getData(), 0, paqueteRespuesta.getLength());
           System.out.println(respuesta); 
            // imprimimos la respuesta donde veremos el puerto al que conectamos

            String[] partesRespuesta = respuesta.split(" ");
            if (!partesRespuesta[0].equals("puerto")) {
                System.out.println("error al conectarse con el servidor");
                return;
            }
            int puerto = Integer.parseInt(partesRespuesta[1]); // nuevo puerto
            //aqui esta la logica, puesto que el cliente tiene un comando el cual se encargara de enviar un fichero
            //entonces el cliente tiene que interpretar ese comando ademas del servidor para recibirlo, en este caso el put, al igual 
            //que con el get se encargara de mandar el comando y espera la respuesta del servidor, el cual sera el fichero al que le haces el get.
            //tambien recibimos la confrimacion de que se ha realizado correctamente el envio o la recepcion. 
            //asi con todos los comandos, el delete o el list, mandamos comando y esperamos la respuesta en estos dos ultimos caso en texto.
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String comando;
            System.out.println("Ingrese comandos ('login usuario contraseña', 'get <archivo>', 'put <archivo>', 'list', 'delete <archivo>'):");

            while ((comando = userInput.readLine()) != null && !comando.equals("salir")) {
                if (comando.startsWith("put ")) {
                    manejarPut(socket, address, puerto, comando);
                } else {
                    enviarMensaje(socket, address, puerto, comando);
                }

                // espera la respuesta del servidor para todos los comandos
                paqueteRespuesta = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);
                socket.receive(paqueteRespuesta);
                respuesta = new String(paqueteRespuesta.getData(), 0, paqueteRespuesta.getLength());

                if (comando.startsWith("get ")) {
                    String[] partesComando = comando.split(" ", 2);
                    if (partesComando.length > 1) {
                        guardarArchivo(partesComando[1], paqueteRespuesta.getData(), paqueteRespuesta.getLength());
                    }
                } else {
                    System.out.println(respuesta); // muestra la respuesta para los demas comandos (list o delete)
                }
            }
        } catch (IOException e) {
            System.out.println("Error de comunicación: " + e.getMessage());
        }
    }
    //logica para el envio de mensajes
    private static void enviarMensaje(DatagramSocket socket, InetAddress address, int puerto, String mensaje) throws IOException {
        byte[] bufferEnvio = mensaje.getBytes();
        DatagramPacket paquete = new DatagramPacket(bufferEnvio, bufferEnvio.length, address, puerto);
        socket.send(paquete);
    }
    // esta es la logica que se encarga de coger el archivo, guardar su contenidio y mandarlo mediante el metodo anterior
    private static void manejarPut(DatagramSocket socket, InetAddress address, int puerto, String comando) throws IOException {
        String[] partesComando = comando.split(" ", 2);
        if (partesComando.length > 1) {
            String nombreArchivo = partesComando[1];
            byte[] contenidoArchivo = Files.readAllBytes(Paths.get(nombreArchivo));
            enviarMensaje(socket, address, puerto, comando + " " + new String(contenidoArchivo));
        }
    }
    // este es el encargado de que al hacer un get, guarde los datos que hemos recibido en un fichero.
    private static void guardarArchivo(String nombreArchivo, byte[] datos, int longitud) {
        try (FileOutputStream fos = new FileOutputStream(nombreArchivo)) {
            fos.write(datos, 0, longitud);
            System.out.println("Archivo guardado: " + nombreArchivo);
        } catch (IOException e) {
            System.out.println("Error al guardar el archivo: " + e.getMessage());
        }
    }
}
