#include <stdio.h>

const int n = 10;
int arr[n] = { 0 };

void increment(int id, int instructions) {
    if (id < n) {
        arr[id] = arr[id] + instructions;
    }
}

void report() {
    for (int i = 0; i < n; i++) {
        printf("Function with id %d, executed %d instructions\n", i, arr[i]);
    }
}


