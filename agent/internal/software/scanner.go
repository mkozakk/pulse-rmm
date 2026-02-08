package software

type SoftwareItem struct {
	Name    string
	Version string
	Source  string
}

func Scan() ([]SoftwareItem, error) {
	return scan()
}
