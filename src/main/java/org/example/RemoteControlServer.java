package org.example;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class RemoteControlServer {

    private static final int IMAGE_PORT = 12346;
    private static BufferedImage lastFrame = null;
    private static long lastImageTime = 0;
    private static final long IDLE_DELAY_MS = 2000;

    private static JFrame frame;
    private static JLabel label;
    private static ITesseract tesseract;

    public static void main(String[] args) throws Exception {
        frame = new JFrame("Экран телефона");
        label = new JLabel();
        frame.add(label);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        tesseract = new Tesseract();
        tesseract.setDatapath("E:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("heb");
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    if (lastFrame != null &&
                            System.currentTimeMillis() - lastImageTime >= IDLE_DELAY_MS) {

                        BufferedImage annotated = annotateHebrewWords(lastFrame, tesseract);
                        SwingUtilities.invokeLater(() -> {
                            frame.setSize(annotated.getWidth() + 50, annotated.getHeight() + 100);
                            label.setIcon(new ImageIcon(annotated));
                        });

                        lastImageTime = System.currentTimeMillis();
                    }
                } catch (InterruptedException ignored) {}
            }
        }).start();

        try (ServerSocket imageServer = new ServerSocket(IMAGE_PORT)) {
            System.out.println("Ожидание подключения клиента для изображений...");

            while (true) {
                Socket imageSocket = imageServer.accept();
                System.out.println("Клиент подключён.");

                DataInputStream dataInputStream = new DataInputStream(
                        new BufferedInputStream(imageSocket.getInputStream())
                );

                while (true) {
                    int length;
                    try {
                        length = dataInputStream.readInt();
                    } catch (EOFException eof) {
                        System.out.println("Клиент отключился.");
                        break;
                    }

                    if (length <= 0) continue;

                    byte[] imageBytes = new byte[length];
                    dataInputStream.readFully(imageBytes);

                    BufferedImage raw = ImageIO.read(new ByteArrayInputStream(imageBytes));
                    if (raw == null) continue;

                    BufferedImage current = new BufferedImage(
                            raw.getWidth(),
                            raw.getHeight(),
                            BufferedImage.TYPE_3BYTE_BGR
                    );
                    Graphics2D g2d = current.createGraphics();
                    g2d.drawImage(raw, 0, 0, null);
                    g2d.dispose();

                    lastFrame = current;
                    lastImageTime = System.currentTimeMillis();

                    SwingUtilities.invokeLater(() -> {
                        frame.setSize(current.getWidth() + 50, current.getHeight() + 100);
                        label.setIcon(new ImageIcon(current));
                    });
                }
            }
        }
    }

    private static BufferedImage annotateHebrewWords(BufferedImage image, ITesseract tesseract) {
        BufferedImage copy = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR
        );

        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.setColor(Color.YELLOW);
        g.setStroke(new BasicStroke(3));

        try {
            List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            for (Word word : words) {
                String text = word.getText().trim();
                if (!text.isEmpty() && containsHebrew(text)) {
                    Rectangle rect = word.getBoundingBox();
                    g.drawRect(rect.x, rect.y, rect.width, rect.height);

                    // 🟡 Выводим слово в консоль
                    System.out.println("🟨 [HEBREW] → " + text);
                }
            }
        } catch (Exception e) {
            System.err.println("OCR error: " + e.getMessage());
        }

        g.dispose();
        return copy;
    }

    private static boolean containsHebrew(String text) {
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HEBREW) {
                return true;
            }
        }
        return false;
    }
}
