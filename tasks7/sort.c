#include <stdio.h>

void sort(int* arr, int size) {
    for (int i = 0; i < size; i++) {
        int min = i;
        for(int j = i; j < size; j++) {
            if (arr[j] < arr[min]) {
                min = j;
            }
        }
        int temp = arr[i];
        arr[i] = arr[min];
        arr[min] = temp;
    }
}

int main() {
    printf("Sorting array\n");
    const int n = 100;
    int array[n] = {};

    for (int i = 0; i < n; i++) {
        array[i] = i;
    }
    
    sort(array, n);

    printf("sorted\n");
    for (int i = 0; i < n; i++) {
        printf("%d ", array[i]);
    }
    printf("\n");
    return 0;
}
