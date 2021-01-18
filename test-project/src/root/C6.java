package root;

class C6 implements I1 {

    interface Inner {
        public String generate();
    }

    class Defaut implements I1 {
        public void log(String msg) {
            System.out.println("X: " + msg);
        }
    }

    public void log(String msg) {
        Inner x = new Inner() {
            public String generate() {
                return "A string2 ";
            }
        };

        I1 logger = new I1() {
            public void log(String msg) {
                System.out.println("X: " + msg);
            }
        };

        logger.log(x.generate());
    }
}