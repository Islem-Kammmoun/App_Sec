import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.net.URI;

@Path("/steganography")
public class Steganography {

    private static final String ALGORITHM = "AES";
    private static final String DELIMITER = "00000000"; // Exemple de délimiteur en binaire
    private static File encodedImageFile = null; // Variable pour stocker l'image encodée

    // Endpoint pour encoder un message dans une image à partir d'une URL
    @POST
    @Path("/encode")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response encodeMessage(
            @QueryParam("imageUrl") String imageUrl,
            @QueryParam("secretKey") String secretKey,
            @QueryParam("message") String message
    ) {
        try {
            // Télécharger l'image depuis l'URL
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);

            // Encoder le message et insérer dans l'image
            BufferedImage encodedImage = encode(image, message, secretKey);
            encodedImageFile = new File("encoded_image.png");

            // Sauvegarder l'image encodée
            ImageIO.write(encodedImage, "png", encodedImageFile);

            return Response.ok("Message encoded successfully! You can download the image from /image endpoint.")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error encoding message: " + e.getMessage())
                    .build();
        }
    }

    // Endpoint pour obtenir l'image encodée
    @GET
    @Path("/image")
    @Produces("image/png")
    public Response getEncodedImage() {
        if (encodedImageFile != null && encodedImageFile.exists()) {
            return Response.ok(encodedImageFile).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No encoded image found.")
                    .build();
        }
    }

    // Endpoint pour décoder un message dans une image depuis l'URL
    @GET
    @Path("/decode")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response decodeMessage(
            @QueryParam("imageUrl") String imageUrl,
            @QueryParam("secretKey") String secretKey
    ) {
        try {
            // Télécharger l'image depuis l'URL
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);

            // Décoder le message de l'image
            String decodedMessage = decode(image, secretKey);

            return Response.ok("Decoded message: " + decodedMessage)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error decoding message: " + e.getMessage())
                    .build();
        }
    }

    // Méthode pour encoder l'image et insérer le message
    public static BufferedImage encode(BufferedImage image, String message, String secretKey) throws Exception {
        // Chiffrer le message
        String encryptedMessage = encrypt(message, secretKey);

        // Convertir le message chiffré en binaire et ajouter un délimiteur
        String binaryMessage = stringToBinary(encryptedMessage) + DELIMITER;

        // Insérer le message binaire dans l'image
        int messageLength = binaryMessage.length();
        int width = image.getWidth();
        int height = image.getHeight();
        int index = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (index < messageLength) {
                    int pixel = image.getRGB(x, y);
                    int newPixel = setLeastSignificantBit(pixel, binaryMessage.charAt(index));
                    image.setRGB(x, y, newPixel);
                    index++;
                }
            }
        }

        return image;
    }

    // Méthode pour décoder le message de l'image
    public static String decode(BufferedImage image, String secretKey) throws Exception {
        StringBuilder binaryMessage = new StringBuilder();
        int width = image.getWidth();
        int height = image.getHeight();

        // Extract binary data from the image
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                binaryMessage.append(getLeastSignificantBit(pixel));
            }
        }

        // Clean binary message to ensure it's valid (only 0s and 1s)
        String binaryString = binaryMessage.toString().replaceAll("[^01]", "");  // Only keep 0 and 1
        String encryptedMessage = binaryToString(binaryString.replace(DELIMITER, ""));

        // Decrypt the message
        return decrypt(encryptedMessage, secretKey);
    }


    // Méthodes de steganographie
    private static String encrypt(String message, String secretKey) throws Exception {
        Key key = generateKey(secretKey);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    private static String decrypt(String encryptedMessage, String secretKey) throws Exception {
        Key key = generateKey(secretKey);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedMessage);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }

    private static Key generateKey(String secretKey) throws Exception {
        if (secretKey.length() < 16) {
            while (secretKey.length() < 16) {
                secretKey += " ";
            }
        } else if (secretKey.length() > 16) {
            secretKey = secretKey.substring(0, 16);
        }
        return new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
    }

    private static String stringToBinary(String message) {
        StringBuilder binary = new StringBuilder();
        for (char c : message.toCharArray()) {
            binary.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }
        return binary.toString();
    }

    private static String binaryToString(String binary) {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < binary.length(); i += 8) {
            // Ensure we don't exceed the string length
            int end = Math.min(i + 8, binary.length());
            String byteString = binary.substring(i, end);
            message.append((char) Integer.parseInt(byteString, 2));
        }
        return message.toString();
    }

    private static int setLeastSignificantBit(int pixel, char bit) {
        return (pixel & 0xFFFFFFFE) | (bit == '1' ? 1 : 0);
    }

    private static char getLeastSignificantBit(int pixel) {
        return (pixel & 1) == 1 ? '1' : '0';
    }
    public static void main(String[] args) {
        // Exemple de test de l'encodeur ou du décodeur
        try {
            String message = "Hello World!";  // Message à encoder
            String secretKey = "mysecretkey123";  // Clé secrète
            String imageUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT0zILjdnHNs_fzSGjh1_mzsGq8wRYOuxoTBA&s";  // Chemin de l'image

            // Créez un objet Steganography
            Steganography stego = new Steganography();

            // Appel de l'encodeMessage et traitement du Response
            Response encodeResponse = stego.encodeMessage(imageUrl, secretKey, message);
            System.out.println("Encode Response: " + encodeResponse.getEntity());

            // Appel de decodeMessage et traitement du Response
            Response decodeResponse = stego.decodeMessage(imageUrl, secretKey);
            System.out.println("Decoded Message: " + decodeResponse.getEntity());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
