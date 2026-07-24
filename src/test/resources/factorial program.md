Here's a C++ program to compute factorials — I wrote it to `factorial.cpp`, compiled it, and confirmed it works:

```cpp
#include <iostream>

// Iterative approach — computes n! using a loop.
// Uses unsigned long long to hold larger results (valid up to 20!).
unsigned long long factorialIterative(int n) {
    unsigned long long result = 1;
    for (int i = 2; i <= n; ++i) {
        result *= i;
    }
    return result;
}

// Recursive approach — computes n! by calling itself.
unsigned long long factorialRecursive(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorialRecursive(n - 1);
}

int main() {
    int n;
    std::cout << "Enter a non-negative integer: ";
    std::cin >> n;

    if (n < 0) {
        std::cerr << "Error: factorial is not defined for negative numbers.\n";
        return 1;
    }

    if (n > 20) {
        std::cerr << "Warning: result may overflow (n > 20).\n";
    }

    std::cout << n << "! = " << factorialIterative(n) << " (iterative)\n";
    std::cout << n << "! = " << factorialRecursive(n) << " (recursive)\n";

    return 0;
}
```

**Compile and run:**
```bash
g++ -std=c++17 -o factorial factorial.cpp
./factorial
```

**Test output:**
```
Enter a non-negative integer: 5
5! = 120 (iterative)
5! = 120 (recursive)
```

### Notes
- I included **both an iterative and a recursive** version so you can see the two common ways of doing it.
- I used `unsigned long long`, which can hold factorials accurately up to **20!** (≈ 2.4 × 10¹⁸). Beyond that, values overflow — the program prints a warning for `n > 20`.
- If you need factorials of much larger numbers, you'd want a **big-integer library** (like Boost.Multiprecision) or a custom arbitrary-precision routine. Let me know if you'd like that version!