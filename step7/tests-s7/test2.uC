int main() {
    /* Test basic pointer casting */

    float * p;
    int * x;

    p = malloc(4);

    x = (int *) p;

    * x = 7;

    print(* x); /* should print 7 */
}