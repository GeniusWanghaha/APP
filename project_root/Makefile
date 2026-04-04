PYTHON=env/.venv/Scripts/python.exe

.PHONY: init run phase1 phase2 phase3

init:
	python -m venv env/.venv
	$(PYTHON) -m pip install --upgrade pip
	$(PYTHON) -m pip install -r requirements.txt

run:
	$(PYTHON) -m src.run_pipeline --phase 1

phase1:
	$(PYTHON) -m src.run_pipeline --phase 1

phase2:
	$(PYTHON) -m src.run_pipeline --phase 2

phase3:
	$(PYTHON) -m src.run_pipeline --phase 3
