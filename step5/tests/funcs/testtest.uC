int foo(int x, int y);

int main() {

	int a;
	int b;
	int c;

	read(a);
	read(b);

	c = foo(a, b);

	print(c);

	return 0;
}

int foo(int x, int y) {
	return x + y;
}
