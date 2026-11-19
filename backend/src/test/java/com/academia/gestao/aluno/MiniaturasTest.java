package com.academia.gestao.aluno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MiniaturasTest {

    private static String fotoDataUrl(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, new Color((x * 7) % 256, (y * 5) % 256, (x + y) % 256).getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void reduzParaNoMaximo96pxMantendoProporcao() throws Exception {
        String foto = fotoDataUrl(320, 400);
        String mini = Miniaturas.gerar(foto);

        assertTrue(mini.startsWith("data:image/jpeg;base64,"));
        byte[] bytes = Base64.getDecoder().decode(mini.substring(mini.indexOf(',') + 1));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(96, img.getHeight());
        assertEquals(77, img.getWidth());
        assertTrue(mini.length() < foto.length() / 3, "miniatura deve ser bem menor que a foto");
    }

    @Test
    void retornaNuloParaConteudoInvalido() {
        assertNull(Miniaturas.gerar("data:image/jpeg;base64,AAAA"));
        assertNull(Miniaturas.gerar("texto qualquer"));
    }
}
