package br.vbrasfoot.sponsormanager;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

/** Bounded local image import; logos become PNG bytes in mod-owned storage. */
public final class SponsorLogo {
    public static final int MAX_INPUT_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PNG_BYTES = 256 * 1024;
    public static final int MAX_WIDTH = 512, MAX_HEIGHT = 256;
    private SponsorLogo() { }

    public static byte[] importPng(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw new IOException("Escolha um arquivo de logo local valido");
        long size = Files.size(path);
        if (size < 1 || size > MAX_INPUT_BYTES) throw new IOException("Imagem de entrada deve ter no maximo 5 MiB");
        BufferedImage source;
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Formato de imagem nao reconhecido");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Use PNG ou JPEG suportado pelo Java");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName()) && !"jpeg".equalsIgnoreCase(reader.getFormatName()))
                    throw new IOException("Escolha PNG ou JPEG");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 12_000_000L)
                    throw new IOException("Dimensoes de imagem fora do limite (max. 4096x4096 e 12 MP)");
                source = reader.read(0);
            } finally { reader.dispose(); }
        }
        if (source == null) throw new IOException("Imagem vazia ou ilegivel");
        double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / source.getWidth(), (double) MAX_HEIGHT / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        var normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(normalized, "png", output)) throw new IOException("Encoder PNG indisponivel");
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0 || bytes.length > MAX_PNG_BYTES) throw new IOException("Logo normalizada excede 256 KiB");
            return bytes;
        }
    }

    public static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PNG_BYTES) throw new IOException("Logo armazenada invalida");
        try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Logo armazenada invalida");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Logo armazenada invalida");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT)
                    throw new IOException("Dimensoes da logo armazenada acima do limite");
                var image = reader.read(0);
                if (image == null) throw new IOException("Logo armazenada invalida");
                return image;
            } finally { reader.dispose(); }
        }
    }
    static javax.swing.ImageIcon fixedPreview(byte[] bytes) throws IOException {
        BufferedImage image = decode(bytes), canvas = new BufferedImage(128, 40, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min(128.0 / image.getWidth(), 40.0 / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale)), h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var graphics = canvas.createGraphics();
        try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, (128-w)/2, (40-h)/2, w, h, null);
        } finally { graphics.dispose(); }
        return new javax.swing.ImageIcon(canvas);
    }
}
