import java.io.*;

public class StartPKG {

    public static String getHomeDir() {
        String home = System.getenv("HOME");
        if (home != null) {
            return home;
        }
        throw new RuntimeException("Unable to get home directory.");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java start <app-id>");
            return;
        }

        String homeString = getHomeDir();

        String appId = args[0];
        String appPath = homeString + "/Applications/" + appId + "/bin/main";

        // Проверим, существует ли путь к файлу
        File appFile = new File(appPath);
        if (!appFile.exists()) {
            System.out.println("Error: The application with id " + appId + " does not exist.");
            return;
        }

        // Формируем команду для выполнения
        String command = "chmod +x " + appPath + " && " + appPath;

        try {
            // Запускаем команду
            Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
            process.waitFor();

            // Получаем вывод из процесса
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Проверим ошибки
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
