package com.saip.blog.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 업로드 이미지를 디스크에 저장한다. 헤더로 형식과 크기를 먼저 확인해 압축 폭탄을 풀기 전에 막고, PNG·JPEG는 긴 변 1600px로 줄여 다시 인코딩한다(EXIF 같은 그림 밖
 * 정보가 사라진다). 파일 이름은 내용의 SHA-256이라 같은 이미지는 한 파일이고, 주소의 내용이 바뀌지 않아 영구 캐시할 수 있다.
 */
@Component
public class ImageStore {

	static final int MAX_EDGE = 1600;

	static final long MAX_PIXELS = 40_000_000L;

	static final int MAX_GIF_FRAMES = 300;

	private static final Set<String> FORMATS = Set.of("png", "jpeg", "gif");

	private final Path dir;

	public ImageStore(@Value("${blog.image-dir:data/images}") String dir) {
		this.dir = Path.of(dir).toAbsolutePath();
	}

	public Path dir() {
		return dir;
	}

	/** 저장하고 "/images/<해시>.<확장자>"를 돌려준다. */
	public String store(byte[] data) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new InvalidImageException("이미지 파일이 아닙니다 (PNG, JPEG, GIF만 가능)");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				if (!FORMATS.contains(format)) {
					throw new InvalidImageException("PNG, JPEG, GIF 이미지만 올릴 수 있습니다");
				}
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if ((long) width * height > MAX_PIXELS) {
					throw new InvalidImageException("이미지가 너무 큽니다 (최대 4천만 화소)");
				}
				if (format.equals("gif")) {
					// ponytail: GIF는 원본 그대로 둔다(ImageIO는 첫 장면만 읽어서, 다시 인코딩하면 애니메이션이 사라진다). 축소가 필요해지면 프레임별 재조립.
					if (reader.getNumImages(true) > MAX_GIF_FRAMES) {
						throw new InvalidImageException("GIF 장면이 너무 많습니다 (최대 " + MAX_GIF_FRAMES + "장)");
					}
					return save(data, "gif");
				}
				BufferedImage image = shrink(read(reader, width, height), format.equals("png"));
				return format.equals("png") ? save(encodePng(image), "png") : save(encodeJpeg(image), "jpg");
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException e) {
			if (e instanceof InvalidImageException invalid) {
				throw invalid;
			}
			throw new InvalidImageException("이미지를 읽을 수 없습니다");
		}
	}

	/** 큰 이미지는 정수 배율로 건너뛰며 읽어 메모리에 원본 크기 그대로 올리지 않는다(1GB 서버 보호). */
	private static BufferedImage read(ImageReader reader, int width, int height) throws IOException {
		int step = Math.max(1, Math.max(width, height) / MAX_EDGE);
		ImageReadParam param = reader.getDefaultReadParam();
		param.setSourceSubsampling(step, step, 0, 0);
		return reader.read(0, param);
	}

	private static BufferedImage shrink(BufferedImage source, boolean keepAlpha) {
		double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(source.getWidth(), source.getHeight()));
		int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage target = new BufferedImage(w, h, keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		Graphics2D g = target.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(source, 0, 0, w, h, null);
		g.dispose();
		return target;
	}

	private static byte[] encodePng(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] encodeJpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private String save(byte[] bytes, String ext) throws IOException {
		String name = sha256(bytes).substring(0, 32) + "." + ext;
		Files.createDirectories(dir);
		Path target = dir.resolve(name);
		if (!Files.exists(target)) {
			Files.write(target, bytes);
		}
		return "/images/" + name;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}

}
