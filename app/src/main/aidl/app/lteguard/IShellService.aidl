package app.lteguard;

interface IShellService {
    void destroy() = 16777114;
    String exec(String cmd, int timeoutSec) = 1;
}
