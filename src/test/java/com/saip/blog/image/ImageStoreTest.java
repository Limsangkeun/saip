package com.saip.blog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageStoreTest {

	@TempDir
	Path dir;

	ImageStore store;

	@BeforeEach
	void setUp() {
		store = new ImageStore(dir.toString());
	}

	@Test
	void storesPngAndReturnsPath() throws IOException {
		String path = store.store(png(100, 50));

		assertThat(path).matches("/images/[0-9a-f]{32}\\.png");
		assertThat(file(path)).exists();
	}

	@Test
	void shrinksLongEdgeTo1600() throws IOException {
		BufferedImage saved = ImageIO.read(file(store.store(png(4000, 1000))).toFile());

		assertThat(saved.getWidth()).isEqualTo(1600);
		assertThat(saved.getHeight()).isEqualTo(400);
	}

	@Test
	void jpegStaysJpeg() throws IOException {
		assertThat(store.store(encode(new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB), "jpg"))).endsWith(".jpg");
	}

	@Test
	void sameImageSamePath() throws IOException {
		byte[] image = png(100, 50);

		assertThat(store.store(image)).isEqualTo(store.store(image));
		try (var files = Files.list(dir)) {
			assertThat(files.count()).isEqualTo(1);
		}
	}

	@Test
	void rejectsNonImage() {
		assertThatThrownBy(() -> store.store("hello".getBytes(StandardCharsets.UTF_8)))
			.isInstanceOf(InvalidImageException.class);
	}

	@Test
	void rejectsHugeDimensions() throws IOException {
		byte[] bomb = encode(new BufferedImage(10000, 5000, BufferedImage.TYPE_BYTE_BINARY), "png");

		assertThat(bomb.length).isLessThan(100_000);
		assertThatThrownBy(() -> store.store(bomb)).isInstanceOf(InvalidImageException.class);
	}

	@Test
	void stripsMetadataByReencoding() throws IOException {
		byte[] withText = withTextChunk(png(100, 50), "Location", "secret-home-address");

		byte[] saved = Files.readAllBytes(file(store.store(withText)));

		assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("secret-home-address");
	}

	@Test
	void animatedGifKeepsAllFrames() throws IOException {
		String path = store.store(gif(3));

		assertThat(path).endsWith(".gif");
		assertThat(frames(Files.readAllBytes(file(path)))).isEqualTo(3);
	}

	@Test
	void gifWithTooManyFramesIsRejected() throws IOException {
		byte[] gif = gif(301);

		assertThatThrownBy(() -> store.store(gif)).isInstanceOf(InvalidImageException.class);
	}

	private Path file(String path) {
		return dir.resolve(path.substring("/images/".length()));
	}

	static byte[] png(int width, int height) throws IOException {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png");
	}

	static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	/** PNG의 IEND 앞에 tEXt 청크(EXIF처럼 그림 밖에 붙는 정보)를 끼워 넣는다. */
	static byte[] withTextChunk(byte[] png, String key, String value) {
		byte[] data = (key + "\0" + value).getBytes(StandardCharsets.ISO_8859_1);
		byte[] type = "tEXt".getBytes(StandardCharsets.ISO_8859_1);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(data);
		ByteBuffer chunk = ByteBuffer.allocate(12 + data.length)
			.putInt(data.length)
			.put(type)
			.put(data)
			.putInt((int) crc.getValue());
		int iend = png.length - 12;
		ByteBuffer out = ByteBuffer.allocate(png.length + chunk.capacity());
		out.put(png, 0, iend).put(chunk.array()).put(png, iend, 12);
		return out.array();
	}

	static byte[] gif(int frames) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(out);
			writer.prepareWriteSequence(null);
			for (int i = 0; i < frames; i++) {
				BufferedImage frame = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
				frame.setRGB(0, 0, i * 1000);
				writer.writeToSequence(new IIOImage(frame, null, null), null);
			}
			writer.endWriteSequence();
		}
		writer.dispose();
		return bytes.toByteArray();
	}

	static int frames(byte[] gif) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
			ImageReader reader = ImageIO.getImageReaders(in).next();
			reader.setInput(in);
			return reader.getNumImages(true);
		}
	}

}
