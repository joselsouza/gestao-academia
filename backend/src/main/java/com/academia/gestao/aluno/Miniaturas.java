package com.academia.gestao.aluno;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Gera a miniatura (JPEG ~96px, poucos KB) a partir da foto em data URL. */
public final class Miniaturas {

    static final int LADO = 96;
    private static final float QUALIDADE = 0.8f;

    private Miniaturas() {}

    /** Devolve a miniatura em data URL, ou null se a foto não for uma imagem válida. */
    public static String gerar(String fotoDataUrl) {
        try {
            int virgula = fotoDataUrl.indexOf(',');
            if (!fotoDataUrl.startsWith("data:image") || virgula < 0) {
                return null;
            }
            byte[] bytes = Base64.getDecoder().decode(fotoDataUrl.substring(virgula + 1));
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                return null;
            }
            double escala = Math.min(1.0, (double) LADO / Math.max(original.getWidth(), original.getHeight()));
            int w = Math.max(1, (int) Math.round(original.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(original.getHeight() * escala));

            BufferedImage mini = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = mini.createGraphics();
            g.setColor(Color.WHITE); // fundo para imagens com transparência
            g.fillRect(0, 0, w, h);
            g.drawImage(original.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(saida)) {
                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(QUALIDADE);
                writer.setOutput(out);
                writer.write(null, new IIOImage(mini, null, null), params);
            } finally {
                writer.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(saida.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
