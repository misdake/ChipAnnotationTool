package com.rs.tool.chipannotation.log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Git {

	public static void uploadToGithub(String commitMessage) throws IOException, InterruptedException {
		Path directory = new File(".").getAbsoluteFile().toPath();
		gitStage(directory);
		gitCommit(directory, commitMessage);
		gitPush(directory);
	}

	private static void gitInit(Path directory) throws IOException, InterruptedException {
		runCommand(directory, "git", "init");
	}

	private static void gitStage(Path directory) throws IOException, InterruptedException {
		runCommand(directory, "git", "add", "-A");
	}

	private static void gitCommit(Path directory, String message) throws IOException, InterruptedException {
		runCommand(directory, "git", "commit", "-m", message);
	}

	private static void gitPush(Path directory) throws IOException, InterruptedException {
		runCommand(directory, "git", "push");
	}

	private static void gitClone(Path directory, String originUrl) throws IOException, InterruptedException {
		runCommand(directory.getParent(), "git", "clone", originUrl, directory.getFileName().toString());
	}

	private static void runCommand(Path directory, String... command) throws IOException, InterruptedException {
		Objects.requireNonNull(directory, "directory");
		if (!Files.exists(directory)) {
			throw new RuntimeException("can't run command in non-existing directory '" + directory + "'");
		}
		ProcessBuilder pb = new ProcessBuilder()
				.command(command)
				.directory(directory.toFile());
		Process p = pb.start();
		StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");
		StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
		outputGobbler.start();
		errorGobbler.start();
		int exit = p.waitFor();
		errorGobbler.join();
		outputGobbler.join();
		if (exit != 0) {
			throw new AssertionError(String.format("runCommand returned %d", exit));
		}
	}

	private static class StreamGobbler extends Thread {

		private final InputStream is;
		private final String type;

		private StreamGobbler(InputStream is, String type) {
			this.is = is;
			this.type = type;
		}

		@Override
		public void run() {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(is));) {
				String line;
				while ((line = br.readLine()) != null) {
					System.out.println(type + "> " + line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

}
