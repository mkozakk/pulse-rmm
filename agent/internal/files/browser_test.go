package files

import (
	"os"
	"path/filepath"
	"testing"
)

func TestListDir(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "hello.txt"), []byte("hi"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(dir, "sub"), 0o755); err != nil {
		t.Fatal(err)
	}

	entries, err := ListDir(dir)
	if err != nil {
		t.Fatalf("ListDir: %v", err)
	}
	if len(entries) != 2 {
		t.Fatalf("want 2 entries, got %d", len(entries))
	}

	byName := map[string]bool{}
	for _, e := range entries {
		byName[e.Name] = e.IsDir
	}
	if byName["sub"] != true {
		t.Errorf("expected sub to be a directory")
	}
	if byName["hello.txt"] != false {
		t.Errorf("expected hello.txt to be a file")
	}
}
