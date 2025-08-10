package work.multithreaded.webserver;

public class DemoRun {
     static void main(String[] args) {
        CustomWebServer virtualServer = new CustomWebServer(8080, 100, true);
        CustomWebServer platformServer = new CustomWebServer(8081, 50, false);

        try {
            virtualServer.start();
            platformServer.start();

            System.out.println("Servers started:");
            System.out.println("Virtual thread server:  http://localhost:8080");
            System.out.println("Platform thread server: http://localhost:8081");

            // держим сервера 1 минуту
            Thread.sleep(60_000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            virtualServer.stop();
            platformServer.stop();
        }
    }
}
