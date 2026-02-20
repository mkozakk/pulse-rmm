package software

type SoftwareItem struct {
	Name     string
	ID       string
	Version  string
	Source   string
	UpdateTo string
	IsStore  bool
}

func Scan() ([]SoftwareItem, error) {
	return scan()
}
