package work;

public class Main {
    public static void main(String[] args) {
        CustomList<String> list = new CustomList<>();
        list.add("1");
        list.add("test");
        list.add("test2");
        System.out.println("first element: " + list.get(0));
        System.out.println("second element: " + list.get(1));
        System.out.println("third element: " + list.get(2));
        list.set(2, "new");
        System.out.println("second element: " + list.get(2));
        list.remove(2);
        System.out.println("first element: " + list.get(0));
        System.out.println("second element: " + list.get(1));
        System.out.println("list size: " + list.size());
        System.out.println("list is empty: " + list.isEmpty());

        CustomList<String> list2 = new CustomList<>();
        System.out.println("list2 is empty: " + list2.isEmpty());

        list.add("test3");
        list.add("test4");
        list.add("test5");
        list.add("test6");
        list.add("test7");
        list.add("test8");
        list.add("test9");
        list.add("test10");
        list.add("test11");
        System.out.println("list size: " + list.size());

        list.clear();
        System.out.println("list is empty: " + list.isEmpty());
        System.out.println("list size: " + list.size());
    }
}
