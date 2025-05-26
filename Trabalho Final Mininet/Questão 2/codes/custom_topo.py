from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.log import setLogLevel

class CustomTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        # Create hosts
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')
        h5 = self.addHost('h5')
        h6 = self.addHost('h6')
        h7 = self.addHost('h7')
        h8 = self.addHost('h8')

        # Create switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s5 = self.addSwitch('s5')

        # Add links
        self.addLink(s1, h1)
        self.addLink(s1, s2)

        self.addLink(s2, s3)
        self.addLink(s2, s5)
        self.addLink(s2, h2)
        self.addLink(s2, h5)

        self.addLink(s3, h6)
        self.addLink(s3, h7)
        self.addLink(s3, h8)

        self.addLink(s5, h3)
        self.addLink(s5, h4)

# Topologies dictionary for `mn --custom`
topos = {'customtopo': (lambda: CustomTopo())}

def run():
    topo = CustomTopo()
    net = Mininet(topo=topo)
    net.start()
    print("\n*** Topologia customizada ativa\n")
    CLI(net)
    net.stop()

if __name__ == '__main__':
    setLogLevel('info')
    run()