FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY device_simulator.py .
CMD ["python", "-u", "device_simulator.py"]
