package org.jcodec.codecs.h264.testtool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.annexb.AnnexBDemuxer;
import org.jcodec.codecs.util.PGMIO;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

/**
 * A tool used for batch testing of the decoder
 * 
 * Compares frames generated by the javcodec h264 to the frames generated by the
 * reference implementation of the decoder
 * 
 * @author Jay Codec
 * 
 */
public class TestTool {

	static class Args {
		File refFolder;
		String h264Name;
		File framesFolder;
	}

	public static void main(String[] args) {

		Args a = checkArgs(args);
		if (args == null)
			return;

		H264Decoder decoder;
		PocketInputStream is = null;
		try {
			is = new PocketInputStream(new BufferedInputStream(
					new FileInputStream(a.h264Name)));
			decoder = new H264Decoder(new AnnexBDemuxer(is));

		} catch (IOException e) {
			IOUtils.closeQuietly(is);
			System.err.println("Could not read h264 source file");
			System.exit(-1);
			return;
		}

		byte[] spspps = is.getPocket();
		is.reset();

		for (int i = 0;; i++) {

			String baseName = "ref_d";
			String nameForY = baseName + i + "y.pgm";
			String nameForCb = baseName + i + "cb.pgm";
			String nameForCr = baseName + i + "cr.pgm";

			try {
				Picture ref = readFrame(new File(a.refFolder, nameForY),
						new File(a.refFolder, nameForCb), new File(a.refFolder,
								nameForCr));

				System.out.print("\nFrame " + i + " -- ");
				Picture frame = decoder.nextPicture();
				if (frame == null)
					break;

				byte[] frameBytes = is.getPocket();
				is.reset();

				if (a.framesFolder != null) {
					File frameFile = new File(a.framesFolder, "frame" + i
							+ ".264");
					saveFrame(spspps, frameBytes, frameFile);
				}

				if (!compareFrames(ref, frame)) {
					System.out.print("DIFFERS");
				} else {
					System.out.print("EQUALS");
				}

			} catch (IOException e) {
				System.out.println("\nFinished reading frames");
				break;
			}
		}
	}

	private static Args checkArgs(String[] args) {
		if (args.length < 2) {
			System.err
					.println("\nSyntax: <folder with ref imgs> <.264 file> [folder to store frames]");
			System.exit(-1);
			return null;
		}

		Args result = new Args();
		String folderName = args[0];
		result.refFolder = new File(folderName);

		result.h264Name = args[1];

		if (args.length > 2) {
			String framesFolderName = args[2];
			if (framesFolderName != null) {
				result.framesFolder = new File(framesFolderName);
			}
		}

		return result;
	}

	private static void saveFrame(byte[] spspps, byte[] frameBytes,
			File frameFile) throws IOException {

		OutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(frameFile));

			out.write(spspps);
			out.write(frameBytes, 0, frameBytes.length - 4);

			out.flush();

		} finally {
			IOUtils.closeQuietly(out);
		}
	}

	private static Picture readFrame(File yFile, File cbFile, File crFile)
			throws IOException {
		Picture luma = readComponent(yFile);
		Picture cb = readComponent(cbFile);
		Picture cr = readComponent(crFile);

		return new Picture(luma.getWidth(), luma.getHeight(), new int[][] {luma.getPlaneData(0), cb
				.getPlaneData(0), cr.getPlaneData(0)}, ColorSpace.YUV420);

	}

	private static Picture readComponent(File f) throws IOException {
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(f));
			return PGMIO.readPGM(is);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	private static boolean compareFrames(Picture ref, Picture frame) {
		if (!compareArray(ref.getPlaneData(0), frame.getPlaneData(0)))
			return false;
		if (!compareArray(ref.getPlaneData(1), frame.getPlaneData(1)))
			return false;
		if (!compareArray(ref.getPlaneData(2), frame.getPlaneData(2)))
			return false;

		return true;
	}

	private static boolean compareArray(int[] a, int[] b) {
		if (a.length != b.length)
			return false;

		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i])
				return false;
		}

		return true;
	}
}