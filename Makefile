.PHONY: build

build: build-readme
	@command -v hugo >/dev/null 2>&1 || { echo "Command not found: hugo"; exit 1; }
	@echo "Building site..."
	hugo

build-readme:
	@echo "Generating list..."
	@for folder in ./content.zh/docs/*; do \
		if [ -d $$folder ]; then \
			name=$$(basename $$folder); \
			title=$$(sed -n -e '/^---$$/{:a' -e 'n;p;ba' -e '}' $$folder/index.md | grep "title:" | sed 's/title://'); \
			echo "Catch $$title..."; \
			echo "- [$$title](content.zh/docs/$$name/index.md)" >> list.txt; \
			echo "- [$$title](docs/$$name/)" >> menu.txt; \
		fi \
	done
	@echo "Updating README.md..."
	@cat README.md.template > README.md
	@cat list.txt >> README.md
	@cat README.md.template > content.zh/_index.md
	@cat menu.txt >> content.zh/_index.md
	@rm list.txt
	@rm menu.txt
	@echo "Done."

clean:
	@echo "Cleaning..."
	@rm -rf docs
	@echo "Done."