import socket

HOST = '127.0.0.1'
PORT = 65432

def run_client():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((HOST, PORT))
        print("Conectado ao servidor. Responda as questões:")
        
        for _ in range(3):
            data = s.recv(1024).decode()
            parts = data.split(';')
            question = parts[0]
            options = parts[1:]
            
            print(f"\n{question}")
            for idx, opt in enumerate(options):
                print(f"{idx}. {opt}")
            
            while True:
                try:
                    resp = int(input("Sua resposta (0-3): "))
                    if 0 <= resp <= 3:
                        break
                    print("Por favor, escolha entre 0 e 3.")
                except ValueError:
                    print("Entrada inválida. Use números.")
            
            s.sendall(str(resp).encode())
        
        result_data = s.recv(1024).decode()
        results = list(map(int, result_data.split(';')[:-1]))
        total = int(result_data.split(';')[-1])
        
        print(f"\nResultado: {total}/3 acertos")
        for i, res in enumerate(results):
            print(f"Questão {i+1}: {'✅ Acerto' if res else '❌ Erro'}")

if __name__ == "__main__":
    run_client()