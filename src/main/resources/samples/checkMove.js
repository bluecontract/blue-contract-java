import { Chess } from 'blue:FaxLLgZHeDhM8WBZitETYBHYB6PFa1iR9q4RuE2PNS5r';
function checkMove(from, to, position = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1') {
    const game = new Chess();
    game.load(position);

    try {
        const move = game.move({ from, to, promotion: 'q' });
        if (move === null) {
            return { legal: false, position: position, gameOver: false, winner: null, draw: false };
        }
        const newPosition = game.fen();

        let gameOver = game.isGameOver();
        let winner = null;
        let draw = false;

        if (gameOver) {
            if (game.isCheckmate()) {
                winner = game.turn() === 'w' ? 'black' : 'white';
            } else if (game.isDraw()) {
                draw = true;
            }
        }

        return { legal: true, position: newPosition, gameOver: gameOver, winner: winner, draw: draw };
    } catch (error) {
        return { legal: false, position: position, error: error.message, gameOver: false, winner: null, draw: false };
    }
}

let result = checkMove('e2', 'e4');
result = checkMove('e7', 'e5', result.position);
result = checkMove('f1', 'c4', result.position);
result = checkMove('a7', 'a6', result.position);
result = checkMove('d1', 'h5', result.position);
result = checkMove('a6', 'a5', result.position);
result = checkMove('h5', 'f7', result.position);

({ checkMove: checkMove, lastResult: result });