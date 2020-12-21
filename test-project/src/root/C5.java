package root;

public class C5 {
    public C4 c4;

    public C5() {
        this.c4 = new C4();
    }

    public int a() {
        return c4.a() - 42;
    }

    public static void main(String args[]) {
        C5 x = new C5();
        System.out.println("Res3: " + x.c4.c2.a());
        C4.staticC2.a();
        C4.staticFunC2().a();
    }
}