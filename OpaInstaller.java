import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;

public class OpaInstaller {

    // Функция для получения домашней директории пользователя
    public static String getHomeDir() {
        String home = System.getenv("HOME");
        if (home != null) {
            return home;
        }
        throw new RuntimeException("Unable to get home directory.");
    }

    // Функция для распаковки архива
    public static void unzipFile(String zipFile, String destDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path filePath = Paths.get(destDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile()))) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipIn.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    // Функция для чтения манифеста
    public static void readManifest(String manifestPath, Map<String, String> manifestData, List<String> platforms, List<String> permissions) throws Exception {
        File xmlFile = new File(manifestPath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);

        doc.getDocumentElement().normalize();
        NodeList applicationList = doc.getElementsByTagName("Application");

        if (applicationList.getLength() > 0) {
            Element application = (Element) applicationList.item(0);
            manifestData.put("appname", application.getElementsByTagName("appname").item(0).getTextContent());
            manifestData.put("appid", application.getElementsByTagName("appid").item(0).getTextContent());
            manifestData.put("appversion", application.getElementsByTagName("appversion").item(0).getTextContent());
            manifestData.put("appstatus", application.getElementsByTagName("appstatus").item(0).getTextContent());
            manifestData.put("minversion", application.getElementsByTagName("minversion").item(0).getTextContent());
        }

        NodeList buildList = doc.getElementsByTagName("Build");
        if (buildList.getLength() > 0) {
            NodeList platformList = ((Element) buildList.item(0)).getElementsByTagName("platform");
            for (int i = 0; i < platformList.getLength(); i++) {
                platforms.add(platformList.item(i).getTextContent());
            }
        }

        NodeList privacyList = doc.getElementsByTagName("Privacy");
        if (privacyList.getLength() > 0) {
            NodeList permissionList = ((Element) privacyList.item(0)).getElementsByTagName("pressmission");
            for (int i = 0; i < permissionList.getLength(); i++) {
                permissions.add(permissionList.item(i).getTextContent());
            }
        }
    }

    // Функция для проверки версии ОС
    public static boolean checkOSVersion(String currentVersion, String minVersion) {
        return currentVersion.compareTo(minVersion) >= 0;
    }

    // Функция для копирования файла
    public static void copyFile(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    // Функция для обновления списка установленных приложений
    public static void updateInstalledApps(String appID, String appName, String appVersion, String installedAppsPath) throws Exception {
        File xmlFile = new File(installedAppsPath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc;

        // Проверяем существует ли файл с установленными приложениями, если нет - создаем его
        if (!xmlFile.exists()) {
            doc = dBuilder.newDocument();
            Element rootElement = doc.createElement("Applications");
            doc.appendChild(rootElement);
        } else {
            doc = dBuilder.parse(xmlFile);
        }

        doc.getDocumentElement().normalize();
        NodeList appList = doc.getElementsByTagName("Application");
        Element appElement = null;

        for (int i = 0; i < appList.getLength(); i++) {
            Element app = (Element) appList.item(i);
            if (app.getElementsByTagName("appid").item(0).getTextContent().equals(appID)) {
                appElement = app;
                break;
            }
        }

        if (appElement == null) {
            Element newApp = doc.createElement("Application");
            doc.getDocumentElement().appendChild(newApp);

            Element idElem = doc.createElement("appid");
            idElem.appendChild(doc.createTextNode(appID));
            newApp.appendChild(idElem);

            Element nameElem = doc.createElement("appname");
            nameElem.appendChild(doc.createTextNode(appName));
            newApp.appendChild(nameElem);

            Element versionElem = doc.createElement("appversion");
            versionElem.appendChild(doc.createTextNode(appVersion));
            newApp.appendChild(versionElem);
        } else {
            appElement.getElementsByTagName("appversion").item(0).setTextContent(appVersion);
        }

        // Сохраняем файл
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(installedAppsPath));
        transformer.transform(source, result);
    }

    // Функция для удаления приложения
    public static void removeApp(String appId, String appsDir) throws Exception {
        Path appDir = Paths.get(appsDir, appId);
        if (Files.exists(appDir)) {
            // Удаление приложения из файловой системы
            deleteDirectory(appDir);
            System.out.println("App " + appId + " removed successfully!");

            // Удаление приложения из installed_apps.xml
            removeAppFromInstalledApps(appId, appsDir + "/installed_apps.xml");
        } else {
            System.err.println("App " + appId + " not found.");
        }
    }

    // Функция для удаления приложения из installed_apps.xml
    public static void removeAppFromInstalledApps(String appId, String installedAppsPath) throws Exception {
        File xmlFile = new File(installedAppsPath);
        if (!xmlFile.exists()) {
            System.err.println("No installed apps file found.");
            return;
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);

        doc.getDocumentElement().normalize();
        NodeList appList = doc.getElementsByTagName("Application");

        Element appElementToRemove = null;
        for (int i = 0; i < appList.getLength(); i++) {
            Element app = (Element) appList.item(i);
            if (app.getElementsByTagName("appid").item(0).getTextContent().equals(appId)) {
                appElementToRemove = app;
                break;
            }
        }

        if (appElementToRemove != null) {
            doc.getDocumentElement().removeChild(appElementToRemove);

            // Сохраняем обновленный файл
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(installedAppsPath));
            transformer.transform(source, result);

            System.out.println("App entry removed from installed_apps.xml.");
        } else {
            System.err.println("App entry not found in installed_apps.xml.");
        }
    }

    // Функция для удаления временной папки
    public static void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    // Функция установки приложения
    public static void installApp(String packagePath, String currentOSVersion) {
        try {
            // Получаем домашнюю директорию и создаем пути для временных директорий
            String homeDir = getHomeDir();
            String tempDir = homeDir + "/Applications/tmp/" + Paths.get(packagePath).getFileName().toString();
            String appsDir = homeDir + "/Applications";

            // 1. Распаковка zip-архива
            unzipFile(packagePath, tempDir);

            // 2. Чтение манифеста
            Map<String, String> manifestData = new HashMap<>();
            List<String> platforms = new ArrayList<>();
            List<String> permissions = new ArrayList<>();
            readManifest(tempDir + "/Manifest.xml", manifestData, platforms, permissions);

            // 3. Проверка минимальной версии ОС
            String minVersion = manifestData.get("minversion");
            if (!checkOSVersion(currentOSVersion, minVersion)) {
                System.err.println("OS version is too low. Required: " + minVersion + ", current: " + currentOSVersion);
                return;
            }

            // 4. Проверка наличия архитектуры
            boolean architectureFound = false;
            for (String platform : platforms) {
                if (platform.equals("aarch64") && Files.exists(Paths.get(tempDir + "/bin/aarch64/main"))) {
                    architectureFound = true;
                    copyFile(Paths.get(tempDir + "/bin/aarch64/main"), Paths.get(appsDir + "/" + manifestData.get("appid") + "/bin/main"));
                    break;
                } else if (platform.equals("x86-64") && Files.exists(Paths.get(tempDir + "/bin/x86-64/main"))) {
                    architectureFound = true;
                    copyFile(Paths.get(tempDir + "/bin/x86-64/main"), Paths.get(appsDir + "/" + manifestData.get("appid") + "/bin/main"));
                    break;
                }
            }

            if (!architectureFound) {
                System.err.println("No suitable architecture found.");
                return;
            }

            // 5. Копирование ресурсов
            Files.createDirectories(Paths.get(appsDir + "/" + manifestData.get("appid") + "/res"));
            copyFile(Paths.get(tempDir + "/res/AppIcon.png"), Paths.get(appsDir + "/" + manifestData.get("appid") + "/res/AppIcon.png"));

            // 6. Копирование манифеста
            copyFile(Paths.get(tempDir + "/Manifest.xml"), Paths.get(appsDir + "/" + manifestData.get("appid") + "/Manifest.xml"));

            // 7. Обновление списка установленных приложений
            updateInstalledApps(manifestData.get("appid"), manifestData.get("appname"), manifestData.get("appversion"), appsDir + "/installed_apps.xml");

            // 8. Удаление временной папки
            deleteDirectory(Paths.get(tempDir));

            System.out.println("App " + manifestData.get("appname") + " installed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java OpaInstaller <command> <args>");
            return;
        }

        String command = args[0];
        if (command.equals("install") && args.length == 2) {
            String packagePath = args[1];
            String currentOSVersion = "1.0";
            installApp(packagePath, currentOSVersion);
        } else if (command.equals("remove") && args.length == 2) {
            String appId = args[1];
            String appsDir = getHomeDir() + "/Applications";
            try {
                removeApp(appId, appsDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Invalid command or arguments.");
        }
    }
}
