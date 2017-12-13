package at.testitest;

public class Test {

    public static void main(String[] args) {

        long a = max(System.currentTimeMillis(), 100);
        String c = "Hallo";
        Example b = new Example();

        System.out.println("LongTime: " + a + ' ' + c);
    }

    private static long max(long a, long b) {
        return a > b ? a : b;
    }
}
